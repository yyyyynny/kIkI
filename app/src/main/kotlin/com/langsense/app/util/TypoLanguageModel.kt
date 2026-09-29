package com.langsense.app.util

/**
 * 한영타 판정용 통계 언어 모델 — "이 라틴 문자열은 실제 영어(또는 약어)인가, 아니면 한글을 영문
 * 자판에서 친 것인가"를 **가설끼리의 우도비(likelihood ratio)** 로 판정한다.
 *
 * ## 판정 구조([judge], 2026-09 재설계)
 * 한영타 가설 두 개(CapsLock 꺼짐/켜짐)를 대안 설명 세 개(영어 단어 / 한국어 글 속 알려진 라틴
 * 문자열 / 약어)와 각각 맞붙이고, 가설마다 가장 강한 반론을 상한으로 삼는다. 자세한 내용은 [judge].
 *
 *   영어 단어와의 비교(기존 척도) = [log P(변환결과 | 한국어) − log P(원문 | 영어)] / 라틴 글자 수
 *
 * 두 로그확률을 **같은 분모**(라틴 글자 수)로 나누는 게 핵심이다 — 한쪽은 음절당, 다른 쪽은
 * 글자당으로 정규화하면 한글 음절 수와 라틴 글자 수의 비(보통 1:2~3)가 섞여 `work`(→재가)가
 * 진짜 한영타 `dkssud`(→안녕)보다 "더 한영타 같다"는 역전이 생긴다.
 *
 * ## 모델과 표
 * - **한국어: 어절 위치별 음절 모델 + 자주 쓰는 어절 기억**([koreanWordLogProb], [TypoTables]).
 *   같은 음절이라도 어절의 홀로/첫/가운데/끝 어디에 오느냐에 따라 확률이 다르다(`왜`는 홀로, `다`는
 *   끝에 흔함). 상위 1천 어절은 실제 빈도와 섞는다(`너무`·`그냥`). 한국어 위키·뉴스·KLUE·NSMC
 *   train·혐오표현/챗봇 말뭉치 약 2,500만 어절로 셌다.
 * - **영어 문자 trigram**([EN_TRIGRAM_TABLE]): 소문자 26자 + 단어 경계 27심볼(27³칸). Project
 *   Gutenberg 29권 + AG News(4,890만 자)로 학습.
 * - **Shift 증인 사전**([lexiconLogProb]): 한국어 글에서 Shift 가 무의미한 키에 대문자가 있는 모양
 *   (`SNS`, `tvN`)으로 쓰인 라틴 문자열 — 한글을 치다 생길 수 없는 모양이라 라벨 없이 원문에서
 *   자동 채굴된다. 판정에 영향을 주는 643개만 담았다.
 * - **약어 글자 bigram**([acronymLogProb]): 사전에 없는 약어·모델명의 일반화.
 * - **음절 unigram**([KO_SYLLABLE_TABLE]) + **구어체 단위 전이**([UNIT_TRANSITION_TABLE]):
 *   교체 문자열을 정할 때만 쓴다([koreanInformal]).
 *
 * ## 성능(학습에 쓰지 않은 데이터, 설정 기본값 70%) — 상세는 docs/한영타_검증.md
 * 한영타 감지 96.4%(한국어 문맥이면 97.5%, 이전 94.0%) · CapsLock 한영타 96.0%(이전 16%) ·
 * 영어 기사 오탐 0.0010%(이전 0.0016%) · 영어 사전 오탐 0.022%(동일) · 한국어 글 속 라틴
 * 토큰(약어 등) 오탐 42회/10.3만(이전 730회).
 *
 * ## 테이블 표현
 * 모든 표는 로그확률을 [LEVELS](91)단계로 선형 양자화해 인쇄 가능 ASCII 문자 하나에 대응시킨
 * 문자열이다(Kotlin 리터럴에서 이스케이프가 필요한 `"` `\` `$` 는 제외). 외부 파일·라이브러리
 * 없이 순수 Kotlin 상수만 쓴다(최소 의존성 원칙). 조회는 배열 인덱싱·이진 탐색이라 저사양
 * 기기에서도 토큰당 O(글자 수)로 끝난다. 어절·사전 맵은 첫 판정 때 한 번만 만든다.
 */
internal object TypoLanguageModel {

    /** 글자당 점수 척도의 기준점(점수 3.0 = 보정 전 70%). 최종 보정은 [CALIBRATION_SHIFT]. */
    const val TYPO_THRESHOLD = 3.0

    /**
     * 라틴 토큰 최소 길이. 2글자 이하는 한글 1음절밖에 안 나와 정보가 근본적으로 부족하다 —
     * 대량 검증에서 임계값을 넘겨 새는 영어 약어 60개 중 52개가 2글자였다(`dj`→이, `dl`→아).
     * 3글자 이상으로 제한하면 그 8개(dna/dns/sms/smtp 등 기술 약어)만 남아 [HangulConverter]
     * 의 작은 예외 목록으로 충분해진다.
     */
    const val MIN_LATIN_LENGTH = 3

    private const val LEVELS = 91
    private const val EN_LO = -23.99214008886613
    private const val EN_HI = -0.009359701923198158
    private const val KO_LO = -28.996529163232964
    private const val KO_HI = -5.15292558604919

    /** 조합 실패해 낱자모로 남은 글자의 로그확률(실제 한국어 단어엔 없으므로 최저값). */
    private const val KO_FLOOR = -30.58149166395412

    private const val HANGUL_FIRST = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val JAMO_FIRST = 0x3130
    private const val JAMO_LAST = 0x318F

    /** 27심볼(a-z, 경계 `^`) 3연속 조합의 로그확률. 인덱스 = s1*729 + s2*27 + s3. */
    private const val EN_TRIGRAM_TABLE =
        "jgj_`g_g^Q^kdlQoQtmoW_QQeWzlj_elDK[pURxWXxD>hmPlNc>e>ldHrRvGMsl:viLIbGjjVtd:P:hJfjabmu_[ZqaPgi_jN[gjYbl^9mNzhO" +
        "]p[`[RWI_|j_fOSnjf]URRROqlE[Enrm?e?H^RRer?oW{P?JWO?kvU=OyKklm==f^goZ=qePoEW=J=isT_]wGGSjGMckgtGMe_XdGRG`GxSYZw" +
        "XQkMUQWqlxOW?sklFY`HKQfuLVXpLLRg[TXLL|LLLLL]LRLYRkhOWW{d?buETfHc_IEXi`ZVM?_Erh^abohXPoGlwhWi^Benkc^f<eNxnkVdyT" +
        "TOqWL[k^ns;YmTbMY;]WuiQnyhTkSjQjTWkgMSBlo_PUYmQvY[eYPPVfpPew_hPePurjgP__PPwqNQKpRIimNTbONnxHcnqY<Q<U<wXMGGMMGG" +
        "wGGRGGTGPSbGjGGGGG|kblqtbmQmKmllkgcYnnsW_VMm[sfTb]pDOmjBjZZ^keTSpsfFGHbC{fHiEuZDnu:Ma]ShQ:benkKQ:ULzPjioWYtTUN" +
        "cp^s@V@jxt@W@_@Tin>LC|>>>s>ML>LnL>_YCNZC>j>_x`X]kjU`nBkkgnbKHaqZKHLBkBxiRRMtMRMrMM_bUigMMUebMbWhM{_`QVnXMSk?I`" +
        "b_eY9Xs[N9R?R?}xNI`vIN]wISWacogbXIWdIIIjljlllllllllllllllllllllllllllTkukXXm^eMbsauU[SrrqXQWSjUko[mcxKQKsKKsKU" +
        "nKQ^jK`]KKrKrp]kYdPP_PPVPPgiVP^rPYVPPVP|jkUUhUUUqU_[UUl[U[gU|UUU[UjnDojrnmjmAXpOkCDOsloRWZSeWvipccpcqcscccccmi" +
        "cmcckcccccyeeeeeeeeeeeekkuvekeeeeeekewu^WkaWiWmWbWWWxuW^^WrWWWWWiladrfUsQPR^xUriW[oisIVIQX_h^NNN~NNTNNNNNTfbNT" +
        "TNaNNNNNgpojdjdjwjddddjddddpdddjdddvsMLDz>>>sGGDD_r>>>RJnD>>oDWoMbVhMbSsMMMSM^[MSfSMMjM`M|mUUUoUUUdUgaUbnjUUUU" +
        "UUUU[b}ofWpfVaVbSYjnmqK=qmpwjlimJbkYYoiYY`mYYmp`yYYgrYYYYYYYullllllllllllllllllllllllllruFQQt@@@w@NO@LxF@FS@m@" +
        "@@g@d^SoQuWM[pG_SQSpfGGOrhGMXYGzsMUMplUUhMMoMMcMMhrMZ^U`MM{G]hfZb`ClZYlag_CMpu{=C=Un`cmaZZmZZZ}ZZZZZZZZZZZZZZZ" +
        "ZZirXXX}XXejXXXXXeXXXeXdXXXXXbggggmggggggggggmgggggggggg{cKJHdQQO[@F^HN@]@`]`U@N@@H~iiiiiiiiiiiiiiiiiiiiiiiiii" +
        "zlllllllllllllllllllllllllll[f^fX^fXaIZuqvPoGsqqobS:YSleZuTaTT]jTZThTcTTh{Z_TTTTTrpLNJxDD_nDDhDDxLD^LRuDRJQDkc" +
        "TZdfTTTZTTTlhxZ^TsZZTZTeTyeR^pe^JSiBZlfqciYotZZKUFU>zqdg^h^^hd^^u^^z^^^^d^^^^^^stZZZqaZZrZZlZdgaZyZZoZdZdZnuUM" +
        "dtVGNt>XYdjlO8h[XgI_G[8yv][orh`>P>>kZrml>kqtVk>>>adffftffffffffffffffffffffff{hfT_uXZfoINk[eg`ETtY`M`Gg=zuEJ?x" +
        "E??s?ZEEEu??EKEs??K`HbxdrgmZZZpZZgdanqZanZmZZZZZsvk^Uti^UmUUUUycbUU^[dUU[^UpjY_`R]_W[7TnwweeLqhdtieVTKkbdfUgUU" +
        "clUUb`c__UUiUnUUUUU}NNNNNNNNNNNNSNNNNNNN~NNNNNetLKEw???u??E`KwP??TNqI?Ni?YdJdRUaT_dJeJUUd`JUihTJRZJJ}kRF<sZ<Fw" +
        "<<iWUsIEko<nVMII<vcjXZg`E?eEKsmc[qHwtvYQ??EU]vcqciciqcccccicccivccccrccorbbbpbbhsbbbbhykbbhkhbbbbbqddddddwdjdd" +
        "dddmddddpdoddddxMmnG[GVV`GGeaaVfG^`_GMMMGG}taZZ{ZZZaZcZZZeZZlaZZZZZmZslllllllllllllllllllllllllll@^ZiT^][eI^kk" +
        "oK^bocpei_H{Xm|R__nRRRiRXj^RlRRk^RjRRRqRpyRZRXRZlRRRsaRrRRc__jRRhRRwnSFFxFFcuFFuFLbNFplF_FTFmFppgmqhl`NWSSmkre" +
        "jUupi[kY_TVttXXRkRRRuRRdRRqRRfXRzR[RRRpr^GG}GGMmGVclGUMGXUGUG^G`GhtQQ_wQQQoQQfQQyWQdeQjQQQbQmr_nqpkjJFINdewieE" +
        "kunekJYOXetUUUgUUUnUUUUUtUUU_c{UUUUUnqYYY{YYYcYYYYto`YY`YYYYYYYmhFFFyFFFsTLOFLk]FVROUFFFzF`t[^LlLLL|LLLRVpLLLU" +
        "LbRLRLXlqKQE|KNEiEEEEE_EEEWfNEKEQEvX`mem^iQhVUonume<mbboav]Sftu`YYpYYooYYg`YqcYhfY``YYYYz[[[[[[[[[[[[[[[[[[[[~" +
        "[[[[[duGWJxAAAuAGAWAtAAANNrALNgPk^FbIfFFgb@dd_FiW@FPhXQV@L@}dTZZhTTwgTeTbTjTjocZZnZTTTzmix[pZbL]BgpjiahXssnHUB" +
        "ZPPezVJsuJJJtJJJJSlJJ]SPJJJJJJdzMMMrMS]wMMMMMn`MgSMMMMMjM]ekeeeeeeeeeeeeeeeeeeeeeeee|WZ`NdO_OkEUY^gRQEXfNEEREE" +
        "E~kbbbubbbzbbbkbkbbbbbnbbbbbplllllllllllllllllllllllllll<_ms?[hX@<lplm<e<wsscjR@GLkvf^OtJEOcEEh[SrPErkopJJEiEu" +
        "pT^Up::po@ljOFu:Lk]xr:@:[CicOK^iSeNk:NbUi^GBZdTdM]:[4}`ZfrU`UTeBrmnvHp[mmpJaTIFbui?NKsq?KpE?k?Jv??efrp???^?twM" +
        "JJqGkWvAGf__pGAphLqAUAmAnrHH[uHHQxHHWj`uNHnYNeH_H[HmXV]aTVt?Jg^leuU[?yhnDoGSShhpYYV|PPPbP[PPPtPPPPPqPVPPP^`VPX" +
        "sEEKoKZgQ[cKEEuVVNYEdE{m_bnsp]^rDUu`QllLXmk^hL8pLsrr]DvFD<oDD^hepq<BoH]<D<e<vh]pqoXk]j]YaXffUPanxb`W4[WwPXa_Dh" +
        "cDJDNg_qJzDrceno]DDDtrKTOpUIml?LpRQuh?mltlKO?[?sPJJPJJJJRJJJJJJJJPJX~JJJJJ^l`hfree`mMXcfmcaFiti[j_;kJy[VfmnN@d" +
        "m4`X^]ghRBsueRR:Q4zncgLo]LqpIIcN^fU9jprl?o9hTyJZ]_ZN_SRAYVhbLgAvb|GbRZAAol=WC}=J=t==PC[jF=YT=RR==X=emXcXmXSglD" +
        "Mg^]cT>`sZ>D>>W>|p?r?qNEfn??OZ?`y^LUvd?MXT?n]aUVsQEY_?MX]_iT?ahbHQ_?E?}e`SVuMMbkMMMeVdMMYVMscMM`i{llllllllllll" +
        "lllllllllllllllSbvc]?ZWr?_sppS?KrorfkWfYWhl[[[f[[bz[[[[[s[[f[[[[[[[[wgVyVbV``]VV`VVqVVccVbVV]VVz{YYYd`YYsYYYnY" +
        "ifY`YYYYYpYYoq`ppqFXObHQrcqHMCvkiZ`mMWRsjWJ?xNHQw??hTToJ?bjJ]???Z?wcPPP`PP~PPPVPP_PPYVPPPPPPPhxereoeeeeeeeeeoe" +
        "eeeoeeeeeeua[tipgmDDTNoFuTAMvipFlAgK_ehbbbbbbbbbbbbbmbbbbbbbbbbb}````i```l````````pf```````}vCCVtCCCtCCCCCvNCC" +
        "NCrCCTpCnt^d^k^g^k^^^^^g^^^j^d^^^^^|i^^^vd^^k^^gw^d^^^^^d^^^^^xUFdNZAXCW99kQflI9}]Zp9]cP9gcLLWVLRLYVLYLLRLLTTT" +
        "RLLLLL~lllllllllllllllllllllllllllr===tC==u====Cz=CFFFd=CMV=SdkXOtU]oaOUUO[XeOO_feOOOOO|Y[[EzKEhg??cGY[M??gIL?" +
        "q?e?xBJVVmBjHLaO{drRBBsopBBBBBSUlllllllllllllllllllllllllll{c]cj]]]k]]]]]m]]]c]]]]]]]vjbhbbbnbbbbbbbbbbbbbbbbb" +
        "bb}P[PP_PPPyPPYP[VPPVPPPPPPPP{llllllllllllllllllllllllllrlllllllllllllllllllllllllllTb^bWUkYwCMntrWh=qkral[=bm" +
        "hsUUUnUUUkUU`UUvkUjUU^UUUvUwp^n^^^^qkd^^^dj^^gj^l^^^^^{mTTdmTT`aTTTTT|sTacnZTTTZTmcRNqbUGPZ:DiisiL:ursHMSFVDwm" +
        "^^^o^^^y^ff^^o^^mi^w^^^^^kkMRG{PGGsGGtGMdVGljGSGGGhGjlhGjnSIAc<GfI]hL<[d{P<[<FYvrfk`jec@F@@hgxoXNmmnauHQTb`vgg" +
        "gggggwgggggtggggggggggggo^^^j^^^n^^d^l|^^^^^^^^^^^jwOCOxIQCsCCCCLtCCCCCaCMCrCYvb[QyQZWgQQQQ[g^QQdh^QQQeQwrSOEu" +
        "EEEuEEPaEnTEJqe]EEETExj^IniR[IpR>p`ou_>jeq`s`>NSvfW^a^WWWtWWkWWqWWjrfuWeWWWxjjjjjjjjsjjjjjjjjjjjjtjjjjswI>>y>>" +
        "Dn>D>>>wD>>>>d>>>gDWVXPQbJJfdDXZ_M_UDSJlMMQDJD~^DDJ[DDqbDDDJDvJDRaDDDWDDR|sVaXwRXKsVCtiq^UCppkKOWCf[Ttiiiniiiw" +
        "iiiiiiiiiiiniiiiiix`YYtYYhsYYYYYsYYpYY`YYYsYgkkkkkkkkkkkkkkkkkkskkkkkkkshWOIZIOIOOIIoTXsI_IW]UOIIO}wffffffyfff" +
        "ffflfffffffffffnlllllllllllllllllllllllllllSb^raTXSiNamlsVkUqqx`ra5WXciQYQgQWQjQQ[QW{QQkQQtQQQsQcrTdTlTTuTTTzZ" +
        "TqTTpZZTTTTTThzKK^[QKKcQKQKQvKKv]c_QKKKKnlL]iaVF?k?DhimVPIthaNQ[FkI{nV`VVVVVrVVfVVlVVe]]|VVVVVbsbbbhbhjbbbhbbj" +
        "bblpbobbbbbzl^^^t^qon^d^dfz^^^l^^^^^^^n``qbj^mMYV[oruamBkxlF[G?HSbjjpjjjjjjjjjjjpjjjjjjjjjjjxvXXXlXXXoX_pX_xbX" +
        "_bbXXXXXXvjWLWwL[LsLLLUYmfL_WW^WLLxRtxLbLyULLtLLLXWnRLLbWcLLR^LhbHHRmHHQpHVHNgyNZHpWdHHHjYx[ZeacTYMa9[qonnm9pp" +
        "lwcrC[FriTcTTTTZkTTnZTlToca_j]ZTTT|WWWWWWWWdWWWWWWWWWWW}WWWWWplVHWzBBBsBBRJBwHHJUBjHNBeB^bec[`KK]bKSWSKeWKKKn]" +
        "KQKSK}YV[LpeJdi>Ikdb]P>VomMGYIeD|fgif`cmS_SYbstAbAyroOAAA]Tdngaaraaa|aaaaaaaaaaaaaaaaaryPPYxPV_kPPPPPuPPPPPPPP" +
        "PPPYhhhhhhhhhhhhhwhhhhhhhhhhhhwhgNmYH]HYHHbdhUmH^pccHHQTS|^^^^^^^^^^^^^^g^^^^^^^^^^^~lllllllllllllllllllllllll" +
        "ll[f`X[Ji[TBSvmxUXJidpJPK<<Vvnk[DtWJMqSDxsRbJDmbQrDMJiDhtGXFu>8tpArdSOg>JmkohCG8bOsqMYjx[eHiTIdSjaH@SgZaB[:VFz" +
        "M^fsPocNWDXn^sAM;rwmamlA;Opc???usH?s??dE?lH?SQri?XQl?xmP<GmIl{mDDaXtaA<d_Qi<U<QMmyXXbn_eXtXf_ddwXXehXkXXXXXne`" +
        "XUUOfZvU]^OgZOOUdtOOOOOO{xWQZ`QQQ{Qhb`feQQQnQlQQQQQ_cDDD}DDhmDjSMJfDDYdcaDODRDij]UntXXRqAazbNgO8Thg_c[8lEspiTA" +
        "w]VDo;A_l[gr;RpJbAA;Q;xkQlmofyXj`g]Xe`LZJlpegQLXOwRKYaKMW>HD>hX}>V9hafoTX_9AimVVHnQLbfBYolKosB_smbWWBRRyXaRZRR" +
        "RRZRRRRRRRRRRR~RRRRRes`int`eJl:]ln[hcSfsieTd:b:wgXkZoa]pnIcgfaggQgns[UK>T@znIeBqQBvq5IcV_eB5^rmgGJFp_wLXaZLLRL" +
        "LRLe}ULRX`t]LRLLLWfr>>C|H>Ct>>H>Hi>>PC>UK>KW>e}U[Uh_UUsUUUUUgUUU[UUUUUdU[eIORuYIIlIIOSIiaIYOrSIIOII{}WWWoWWW^W" +
        "WW^WlWWWWWfWWWWWqvY[F{FFZnRSF^OqQ^VOVhSFFFmmlllllllllllllllllllllllllllXdr_UmlmnQjZqtOvEikM^kh_mhl____p______p" +
        "__{r____h__h__rddmddddmtdjjdmjuddddddddddxoicclcccccccccocccuciccccoyn^zbglFXFVdb^mdVFrnrZ^oFLRabbbbnbbbbb|jbb" +
        "bbbbbbbbbbbbplllllllllllllllllrlllllllllf[[[b[[[[[[[[[b[[[[}b[[e[[lmiNd`agjTWcfxyN[Nggnb[YNbNmmtggggggoggggggg" +
        "ggggggggggzxdddrddddddmjdddddddjdddddwugggggggqgggggmogggqggggggwfffltfffffffffffffffofffffzhbhbbbhbbzbbbbhmbb" +
        "hbbbbbnbvatcRoI]usIfhToQSCtmXo^TCpCbZZZZxZcZZZaivZZZZaZZZZZZZZxllllllllllllllllllllllllllld[[[b[f[b[[[bb[[[[[[" +
        "[[[[b[~``f`n``f``f````i```|f`````qffsfflffqfffffffffffffffffzeaNtYDdMaQ[mppDaDtyfDgPNDDWeeyeeeeeeeeeqeekeeeeee" +
        "eeeewriiiiiiiiiiiitiiiiiiiiiiiivkkkkkkkkkkkkkkkkkkkkkkkkkktuiiioiiiiiiiiitiiiiiioiiiiikkkkkkkkkkkkkkqkkkkkkkkk" +
        "kktlllllllllllllllllllllllllll`r^j_YpdcQYjdrXbHvqpa[dRhmtpTTTwTTTfTTTTTpaToZTzTTTTTjs___n__xe__p_ls__leeh_____" +
        "soXXkoXbXdXXXXX|bXpXX_XXXXXqXRMtoXLOaH;jWpegAtorR_^ClPxzTaTZTTTpTTjTTuTTaTevT^TTTTrjb[e[[[b[[h[g[[[y[[q[[[[[wy" +
        "NNYnNNNjNNZgdwNNbljeZNNTTrhShlj[PMUELtc{Tc?eohLOY?L?kwdddxdddddddddldddddsdddddqpVVo|VVVmVb`VhpVV]eV]VVV]VfvKK" +
        "KxTKKsKKKcKhKKKQQjKKKvK^|^UUoUcU^UUUUUd^Um`U^[UaUUteDDDvDDDpDDDDD|DDDDD`JDDPDO`kcl[lWdkOhghtd`IwsgomdIZdquXXXi" +
        "XXgvXXrrXwaXaXX_XXXXXejjjjjjjjjjjjjjjjjjjjujjjjjswNNNsNTNqNNNNNnTNYTNyNTNcZjVVXLXHcc_BRS`QhTBHbeSPaB]B~lQQQc]Q" +
        "riQWQQQ}^QfWQ_Q`QQQejcfldfTb_TipohZuNtiuNNoNNngv```l```z```f`j```q```````p|WWWnWWhgWWWWWsWWcWW^WWWWWafflffffff" +
        "ffffffoffffffffff{dfWbbKQYiKQmVQxiKe_]`KdKKQzkkkkkkkkkkkkkkqkkkkkkkkkkkqlllllllllllllllllllllllllllNjpmPUgbnGb" +
        "WkuSePrsrlam]qbkvP[Pv^PPkVPgPPwVPieVpPXPjPpqMVneMSoaMX_MSzMSZk^pMMM_atZQ[AoSKZn;;bYhbN;hiQRAb;U;}scmrfhj[^LSWi" +
        "m_`FlslXh`cgOxeTTMkQTNjDDUPM`[Dc`bcDcDJD}wQaQvQWWvQQQQQnWQsWQcQQQ^QntU[UmUUUzUUUUUudUU[UgUUUUUnogphoooTMVoZjvo" +
        "i_WnsUl=ZQfhubbbmbbbhbbbbbjbbbbh{bbbbbpjQOMtMG^tOGYd[dGGOxMQGUGfGxmVVTrPIPs@IGZ[n[hRlQeJX7p@znIRIuI^RmIIghcyII" +
        "In^^OOI_IviYPV|PbPhPVPdcaPPfVPiPP[bPviimdW^nP_J^RfttkYoslmltFjMgjHHHt^fupHHiXSeHHcp_NHHHXHyVMMMMMMMSMMMMMMMMMM" +
        "M~MMMMMahWTN|NNWhNNTNNrNNNWNdNNNsNf`WVIpWF`f@WIT@rTFISla@F@N@}lJSioJ@qqF@U@OlM@gnQf@S@m_{hooss`jBelgerp]^KlrsB" +
        "ZLhQTkrIIO}IIIrIII]IgIIXXISIII`Ib~MMMiMMZgMMMMRkMMZWRRMMMRM[bbbbbbbbsbpbbbbbbbbbbqbybbvVV]fXSMPgAMRkbZT;RhXT;b" +
        "D;X}pj__r__vk_____v___________wlllllllllllllllllllllllllllW^jo^Qk]niqm`wW_Dtmq^XR^oajrOHOyQHXrBHsSZlVBmkTiKBBQ" +
        "BqrasreaqjnNkriqka^^heNN`NNNtpYdSqSYSeSS]dSYSShgg]SSYSS}j^ZqhR]XT7Mfjv]P?romFJ__VMy^QaQiQbQpQQWQ_|ZQZ_]mQQQZQr" +
        "iXXXmXgXhXXexjoeXnbX_XXXXXyo```t`````````v```f`m```hwtfRsnf_nJIJcuWwQGOjtoYECdS]hw```{`````````f`````f`````fic" +
        "cckcioucccicvcccmsccccccuinPPvPPPtPPZPqfPPPlPPPPPvPttC`CxCCCvCCbXIsCCC^CsCCChCjuUY]w_OOtOOkOOdOOOnOaOOO`OwZkhj" +
        "RPZ`aVddkwiUWwsonpUIY`fw_NTqWJhs=TsNMoO=ninp==HW=qkkkkkkkkkkkkkkkkkkkkvkkkkkkgFOFULFF_FcFFLcOFFw_FFFFOF|[UXSvi" +
        "S]YBUMWhhZHUNmgBMB]B|hfgbsiWvhWW^Wbh`Wmk^Wo^WWWyTZvf`adVPUZqhrAU_oymAJAAGW^]]]]u]]]j]]]]]]|]]c]c]]]]]moU[klUU[" +
        "nUUUUUtUUU[UUUUU`U|ggggggpgpgggggsgggggggggggyeBN]_OOBLNOaLOQBH]r_LBHBHB}|aaagaaagaaaauaaaaaaaaaaaaollllllllll" +
        "lllllllllllllllllTielUWncfc_vooNkAllvfd^Ea[rxNmTyNTNgNNeNNpNNmjNoNNN`NZcCF@{EJrq;Vm@@lJCkTiaM@;m@onVKGqVHZnATd" +
        "YWkNCfmMhCW4^A|kXgriacUdDQgbd]^Qqto_jskj`wnDbQsODDvDDtJ]xMDn[PoDDDMD^dUN[oRJbgGSjNP^Q@dmidBO4UG}zMZMrMMMoM]tMV" +
        "sMM]dVoMMMVMkoYp]njqURJc`gwo`^TsseiEaFhimLLLnLLVcLLLLLuLLLRL|LLLLLkjNNBtdOVrWBkUif]BmsUTMNV_HzkM^VoDJDvDDDPDnD" +
        "DODD`WJD{DjrHTH}HHHeHHNHHlQHNHNSHHHHHgoFIUxYLSw@RFFZv@@@cFk@F@k@nUfg_[ZZObBaklid[9pdypktUZLtpP^biPPdlPPtPPzPPu" +
        "__kPPPPPePPPPPPPPPPPPPPPPPPPP~^PPPPopNWNuNNNvNTNTgv]NNTTdN_NvNdgYfMpbSiq:a^]JjhD:VsnTi:ZBzmKUMtNWkrEFk]QpL6opU" +
        "eBY?eOyqarcvneLkTe`tfc^DkmsQLLpDRdnMDP|DDDuDDDDDqDDJJJaDDDbDayOUOrXOqvOaOOOoOOjbOUOOOOOjTTTTfTTT|TT]fTTcTTZkTT" +
        "TZTTukf[ZXAP^aATRdAiNANhnWXgNAA}uRRXwRRfrRR^RRpXRX^X^RR^pRvlllllllllllllllllllllllllllSPuvC_M^]CmqelZaVuosQUC_" +
        "IKgsn_QuHQWsmBrBVmHB_theeXBaBqoFp@q@Fkq@z_@@i@FmWrn@H@O@es_XkqUicm?UaYafV?anRsE`?qSyPgbjX]R]oGYhemG^Mfzm_lWYcG" +
        "vX<NK`rFGj6EK?CX<6OVoN6<6B6}i[Q_tSl`qQCsVmd^CukakNXCtCqqIIOhIISpIWbXzjOI[^I]III[IwSRqnUTXAGLRwPzAdLbpkGPAPG_`t" +
        "PPP}PPPePPPPPlPPZPPVPPPPPkaPTKxNAfsAXgXA_GAPpWdAMAnAyl]]vre[Qs;cteWqc;ShemjV;lUollXHwdHJoKKQpYftAXcTR>SDf8xlMh" +
        "nrgnLgUUj[agZTTqnTdYMdWzHSSuWelUV;voppTlQplo;UD;XVpaK]=yQNjnK[sfNlq=doliCC=fJtYYYYYYYYnYYYYYnYYYYw{YYYYYqj_hos" +
        "ShTl:nlmjei>hmrYKaDjQxiThNv;FdpMY]_]pj_LswcMVHbFpig[GsMNvnBIb[NjS:ajlT?W:_DzNhecNPqJ`MQsVt>jGtqu5U;FENsk=CC}==" +
        "=q=JRObd==FY]F===PHnl^WhtVF[nLIkRv_R:TpiH:O:XFyVi]JniPSrJJbJPXPJPaZJ_VokJ|scigugQ]kT^gpanFFRrLfOLFFUxnPPa{PPgv" +
        "PPg]ioPPPYPPPPPldilllllllllllllllllllllllllllWdqcSGeMoAiofuRjTwqpi`]PlJekWWW|WeibWWfWWqWWliWmWWhbWkcYZTTN`mhNN" +
        "dNNrWNNtpWNWNTT{~N[N_fNNXNNXNNe]N[TaNTNNNNhrQrpj]]?YLMfVto]YwilRDXNZRonWWWrbWWzWWkWWkeWm^`sWWW^^oySSS_SYS_SSbS" +
        "SSSS{_S`SSS`SnpDNJtDSJwDD[N[xUDgcW^DDDpDpe?sjqRc???bnXvqjYqhuYZ?`EYclffftffffffffftfffffffffffxeYYYpYYYzYeYYcs" +
        "YYYYY`YYYYYvy=bGx===q===N=q===JGj===m=]pN`T{VhNVNN]NNjNNN]NTNNNTNwgXkXxX_XcXXXXXiXXXkc_XXXaX{WFefbLMDp;jrYqljA" +
        "wvliboPKDckJQ@xFLSr@HtLSvL@q^FUJFFl@eccccicccccccccccccccoccccc}k:FExEE@v:E:@Sy@:IP@aE@:SGWbD_NrdYfhDVcJTfcDPU" +
        "kh_ZDhD}pBlBvHBevBBcPTmHBZjKnVOBgOwSt]a^a^GU[SshmAcAtpyAAZAQb]qccciicccccccciccoclcccccc{|[k[gai[o[[[[gk[[q[[[" +
        "[a[[[gQQQQQQQQQQQQQQQQQQQQQQQQQQ~^]aLZLbRm[R]XRkRLtYaLLrRLL|hhhhhhhhzhhhhhqhhhhhhhhhhhhlllllllllllllllllllllll" +
        "lllldSSaySSSuSS^YsSScSinY`uSYSdl___g_________d__dq_______}}[[[[[[[[[[[[[o[[[[[[[[[[[[sjjjjjjjjjjjjjjjjpjjjjjjj" +
        "jskkkqkkkkkkkkkkkkkkkkkkkkkktkkqkkkkkkkkkkkkkkkkkkkkkkktllllllllllllllllllllllllllllllllllllllllllllllllllllll" +
        "[ZNWTNNTNNNTT`NXNNsNNNNNWT}llllllllllllllllllllllllllllllllllllllllllllllllllllllh_______e_____h___jq______}vk" +
        "kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkqkkkkkkkueeeeeeeeeeeekeeketqerkeeeeylllllllllllllllllllllllllllpjjj" +
        "jjjjpjjjjjjjsjjjjjjjjjsiiiiiiiioiiiiiiiiivioiiiiiuvccccccccccicccccccccccccc{yWWWWWWWWWWWWWWcWsWWW^WWzWhvT>Ju>" +
        ">>w>>>>>w>>UI>>>>>S>Uiiviiiiixiiiiiiiiiiiiiiiiii____}___h__________________kkkkkkkkkkkwkkkkkkkkkkkkkkkjjjjjjjj" +
        "jjjjjyjjjjjjjjjjjjjllllllllllllllllllllllllllllllllllllllllllllllllllllllLjqoijk`pT[rkt[kngjtcjjOhdjvO`ItII`uI" +
        "VjISrIIdkIsIIIkZqi@@Iy@@xnFfiXSk@IhSem@@@iNctSRLrKBTqBOiW[gR<Zs[^B`B_<zqcmqoig`c_Uljl[l`csklhfS]VwsWUPuUHHoH^i" +
        "UHuNH_fNxHPHRHmpAAG|AAiqAQ^ISlAGbZGkAPAmAn{MMMsMMMiMSMMMtaMSVSgMMMhM^nisorenFLRggltlhMMqr`nG^Ufdw___vn__h_____" +
        "s_____u_____pgTRUwaU]oFUc_f`ZILqN^SSKZAzlXGxoZGRtAAAAKmTAAi^`AUAwAmvT^Fu[FTq@FdNYkS@Fr_iLU@n@vsTTSw`C_uIS[qUkW" +
        "VFn`W>V>Z>vkkkl^lhVcdfjtpmoLiqlsmoZdZijKWEnWWggEEpTVvOEui__EEEQEyzWWWWWWWeWWWWWWWWWWWyWWWWWrp???wE?Wx?EHN?tM?U" +
        "T?iHH?t?`e]WmpFHhk8USTMj[O8XreEQ8Z>|pWUJtabsr:CegghY:bqNlCQJm]waiqnm^rMlEUpntSp?^vl]YPPTXcrDDDyDODzDDJDDhDDDRD" +
        "JJDJ]DZ|QKTnKKmrKKKKKpKKbKKKQKKKK]aaaajaaayaaaaaaaaaaaaaaaaazfdQeXIKGl=N`_Te]=FejNZ`==K}{dUUuU[ljUUc[UpUUU[UUU" +
        "^UiUllllllllllllllllllllllllllllUehhLhhVxGbpnqVgHigqhikQsCjxMsMoMMMcMMaMMnSMk]MwMMMlMltIM@o@Otr@IiL@wI@s^Fo@J@" +
        "Q@g~TIC`SCC_CRVCSdSCYMCCCCCCCcpaorpZVYbCUpfpYgaqnmYiZ__EwvdU`tLLLtLLZLLsUL[UruLLWlLfi[RR^RR^vRRaR^m`Ry_ivR^[RR" +
        "mtU[OwQEHs9W[gZuUEfOYhT^9]9uollsg^oUB?Rlhus_NjnrSkGjCeaip``t``n````o`r```f`o`````ylOEEwQ[YvUE_N^^KEYnpgUKEpEvw" +
        "LLCtCICvCCQLCsCCPCNnLCCsCcyb[EpUEEvEEUEEsTEEfUgEEEVEttVROwIIInII[OIsWIITglIXO`Ixd`lZ^rPU[JNqtuka:nWbrgWh[@tuMQ" +
        "OxKHes>DkF`tF>oZKk>Lkh>gOOOOOOOOOOOnOOOOOOOO~OOOOOd|KeKkKKKqKQKXKhQQQaKpQKKKKjnSV@tcGUvCHaa[mYEVNWm@[:cCysRUEs" +
        "RKRqEEdbTrX:skFhBWBaJxkormmjhRnBWmprNr<vn[XXE<E[algUUxUUUyUUUUU[UUUi[UUUU_UutGGGzPGQvGGGGGpPGSSGgGGGGGbaaaaaaa" +
        "aaaaaaaajajjaaagaaa}]cimXGUG^`GmskMUGny[QGMGQGwh_n_n_____hkk_o____jk___v_ylllllllllllllllllllllllllllKmmeRcl_r" +
        "UqsftUdKsgs`Z^fgGn}MSVjMMMeMMeSMmMMoYMhMMMWMfhKNS]EE}[ENdEEkOE^VE]EEEoSjk[XX_RRRdR_RRXsRRdlReRRRRR}kYjtfXaYXKP" +
        "nkpWe@yoWU_^aJQtmNTNbN`NuNNoWNxNN^[axNNNTNk{SSSpSSS_SSYS`vSSiSaoSSSSSfrGVZ|TBLo/G[ROkIUjaGf=X:]/rk^q]lggECDPmq" +
        "ux`XflkZnE@L_gvdddudddddddddtdddddoddmddru^^^r^d^x^^^ddu^^^i^k^^^^^onPLF{@@@m@@N@@jF@@IFM@F@x@]vJJJ{PJJgJWmVJp" +
        "PJJYP`JJJJJnqLLL}RLXqLXRLLcLLUR_hLLLRLkRaigXRcCXHaginljBr`_hShR[F|kMcSfUMlbMMkUMrMMfWUr_MMMM|fffffffffffffffff" +
        "flf|ffffffxNE@s@::u::EF:u:::R:q::CqL[Y`bQgHGYf:^N_RdZ:@LabUTOUQ~r>K>x>X_r>>vXQll>ggDUFL>j>nnefpsc`M[>Ncjobc>{g" +
        "jLH>RHTaZRRReRZRjXRZRXZXRRpRRRRR]R}tAAAuAAUoAAAGA{GAQAAPAAYPAWg^^^^^^^^^^^^u^o^g^nr^^^^^zdHdEWUME`?WhWYSk?c^VK" +
        "ESHE?~g`QWxZibgWm[aQ_bQQ_`[WiQbQ{lllllllllllllllllllllllllllBf[l[RiSkBkxVlKHByboBWYBaBfloYdjCL`jpOykIlcC]rqiU]" +
        "C[CpiRo@rN@yk@sn@J^@@`]uW@@@bP[pUTrvRtauVCePUcKCViC`OSCpTr]^SpjccTUE?pSoKZHkxdYRJPVaxuKWKX|KK[gU^KYbKKQKh]KKKK" +
        "Kiga@FmPq|`FFd^[c@@HiHl@R@F@py[[[nb[[l[[t[gp[[lheh[[[[btTWtmmRUAJTPsTpVjGrswJeAQA`f}RRR[RRRqXRRRRnRR[RRhRRRRRk" +
        "nQKKvKKdlKZTTKuKKs^T]UTKKKyqRWymdaXi;asYdc^;PhqXOV;fArquVTuWOLkMAXqhjsGNmXcXLATAuharxkdnasYfgak]cUbjs_eb?UOpEK" +
        "EfEKOVZKSQU^EUEdZ~lUKEfEdb`bnmMeah>KgQ>ps>hlnY>_>[>{i^^^^^^^g^^^g^^^^^^{x^^^^^dlclltclXqAd``qmiNlroZfH8iLtjdcS" +
        "uFYoq?_jJ_[jL?nve?E9`BweZdZu[XopDNeYTg`>_kkiAXGdNz[[by[[[j[[[[v[[[[bjdf[[[[[xmTTT|TTTrTTTTZbTTokTTZTTTTr}jZZfZ" +
        "ZZfZaZaZZZZZZZZZZZdZrc`OOoOUfcOOOOO`OOOUasOcOOO}_jLWoLgRsLLWL_mRLZsZaLLLLX{hkbRiR^jfRRXnpeRRR[RsRRR_yullllllll" +
        "lllllllllllllllllllObmgUHlSoHbwcvQaBooseKBSUZogggggggggggggggggggggggggg{n^^id^^wgj^^^^i^^vdd^^^d^^xcU_UUUUU_U" +
        "UU_UUUUdsUUUUUUU}aLRnSHZ^c<<mfrME6ypcC<NWb[xeyeeeeeeeeeveeeeekeeeeeeeesxgggslggggggggqgglggggggggokeeeeeeeeeee" +
        "eeekee{eeeeeeetiZurpKbEb_apRuoQ=nsmKj=I=Wbkkkkkkkkkkkkkkkkkkkkkkkkkktxgggggggpgggggvgggmgggggggg{]VVgVVVi]V]VV" +
        "wVVVVbVVVV]Vnicccuccvicccccccccccccxcccq}W`WbWWWfWWWWWdWW^^WiWWWWWlPSjhZDdJvMhuXgUDSr_uqDlVmSlh]c]]]]]]]]]]uc]" +
        "]]e]]]]]]]|llllllllllllllllllllllllllls_X_yXXXkXX_XXsXXXinXXXXaXv^Y[SYSSSYSrYSgSSSS[b[SSSSS}^^^^d^ddk^^^^^^^^^" +
        "o^^^^^^^}UUUUnU_UiUU}a[_UUU_fUUUUUUo````r```u```f`f```f``f``{`hdddddddddddddddddddddddddd}eeeekeeeeeeeeeeeeeee" +
        "eeeeee|dZcY^YPZiPPVPfVPPP]VPYrPPP}ffffffffffffffffffffffffff|lllllllllllllllllllllllllllIYW]HSbSjJdo]n9_?uzmZd" +
        "NWsSasWpWr^WWlWWcWWzWWnWWiWWWWWm|d^egW^m^WWWWWsW^jcWcWWWWWk_PXPwPPPiPPfP]uPPjoPdPPPePynkJssGXGh:@q^o:`:wna:hEH" +
        "X@rhTeTaTTTdTTe`TvdT^TT|TTTTTmuazakaaaoaaaaniaakiafaaaaaqu;;;w;;Dw;;;;;v;;IAAK;;;k;FGDgiWh]?KJTuat?]9imz?X9O?Z" +
        "dsggmmgggpgggggqgggggwggggpg[_UUoUU_tUU[UcUUUUz[UUuU_UsgTKWyQKKpKVTKKYZKQlWXKKKuKwzcqmnUdU`UUU^UmfUclt^U`UUUlL" +
        "`YWpYa^eBBkQGL]BXnaLBbBZB}GXEH`MJIB<_dnon[<yTWvVV<<Kut^^^l^^^m^^h^dty^d^^f^^^^^rqkkkkkkkqkkkkkkkkkkkkkkkkkktOZ" +
        "ItIIIzIIOOIvIIIROaIIIeIfV[SZmkX^b^b^]ObmDSU^oUbMYD}nNbNbTN}`NNNN]rNNT_NaZNNNNeZZaeccZceZZgmyZgaomuZZZcZrlhhhhp" +
        "hhhphhphhhhhhhhxhhhhhrVLLL`cLLaLLLRLaLLLLLLLzLVYzeeeeeeeeeeeeeeeeeeeeeeeeee}lX[R|R^RRRRc`qiRRRk[RRRRRRsiiiiiii" +
        "iiiiiiviiiiiiiiiiuiilllllllllllllllllllllllllllLauLRWjLLLLhwsLRLhwlRgLLLLfYYYYYYYYYYYcYY~YYYYYlYYY`Y`aJJJyJJvs" +
        "JJtPJgJJaJJqJJJJJUwgggggggmgggmgggggggggggggwIIzvIITIaIOhjf^OIrr`ORIIVIjr_i_____l____g|i_g_________oflfoffffff" +
        "llzfffffffffffflyVVVVVVVzVVVVVmVVVV]iVVVVVigjtgoS]KnKKlotsQKZtpXjShKKlllllllllllllllllllllllllllljjjjjjjjjjjjj" +
        "jjjjjjjjjjjjjy^^g^x^^^w^^^^i^^^^^^^u^^d^syZZaZZZZdZZsdZZaZZnZZZZZZZyjdddjdddddddddjdddddddddsd{WWWsWWdWWWWgnyW" +
        "gWtoqWWWWWWnqDLDzDDDeDDuLDqDDrQDYDDDDDl____________________~______ffflrffoffffffoffffflffoffyj^k^u^^dg^^^d^um^" +
        "^gxg^q^^^n^[RRvFFllFF^FLfFFtaFfFaFeFzykUfcUUUUUUqbUUaUymUU_UUUUkXXXXXXXX|XXXXXXXXXXXXXXXXXwjdddvdddsdddddzdddd" +
        "jdddddddSSSSSSSSvSSSSSmSSSSSSsSuSSyaZlZZZxZZZZZZZZZZaoaZZZZZZ{llllllllllllllllllllllllllllllllllllllllllllllll" +
        "llllllO_`jMYiqQGcqcwdUGtpfQVgGG`urNNNxNNNeNN_NNyNNpNNgNN_iNekPPPoPPw^P`wPPxPP[PP^PPPPPiiNNNwNxNoNNXNtkNNphNNSN" +
        "NVSnx_?phPRK]??gZfRL?swqR[WEMKnlXXXkXXXvXXmXXpXXoXXzXXX_XXjcYcyYY`nYYknhrYYoYYtYYYYYlrZZZsZZZaZZZZa|ZZZZZeZZZZ" +
        "ZZGLGLlGOGGGLLQ~O_G]dZGXGGGQfrccc{ccccccccciccccclcccccqu^^^y^^jqk^^^^k^^^^^d^^^^^vsMSMxMMYsMMpSMt[MMaMmobMgMn" +
        "rn`dvIIIeIIRbmozIIXOZIIIIIlrXukwUc_oUUPPpqPPPfj[UPgPPsFINV=gS==QXTUmNM=rYc}QQ=F=jpMMMyMVtmMSZM^lSMlgwVMMMMMVll" +
        "llllllllllllllllrllllllllsOOkr`UUzOXUZhtOOchZcOOOXOjZAUGqSA_mA_eYAbSZA^vNNJAPM|bLLRpLL}jLLLLLrLL`VRRT`LLLdmYdo" +
        "`VpV]PyhlpPfPiqePbPPPpnu^^^|^^^l^^^^^h^^^^^^^^^f^fxOOOtOOvlOOOOUwOOcOOOOOOOO`hhhhhhhhqhhhhnhnhhhhhhhhhhyuccckc" +
        "cczccccccoccccicccccpj__j{__ht__j__e__ee_____eemlllllllllllllllllllllllllllksdaRddapVjeifIdOt^t]R_IaZxqaaazaaa" +
        "maaaaavaagaaoaaaaaayeeeeeejeeeeeeseeeempeeeesetaaaiaaaaaaaaygaapaaloaaagsn[gviUYLZL[fftFYOsnbURWFSFxkkkkskkkkk" +
        "kkkkkkkkskkkkkkkkoeeezeeeveeeeejeeeeeeeeeeeeuYYYwYYYnYcYYrsYYYcYlYYYYYud]bblohVQWQwpyibVib`QTaKQknpjjjjjjjjjjj" +
        "jjvjjjjjjjjjjjjnaaasaaazaaaaalaaaaaaaaaualcVbVzVVVyVVVVVhVVV]VVVVVhVkz]]]f]]]s]]]c]s]]]]]i]]]]]ut[[[y[[[k[[[[[" +
        "e[[[[[b[[[s[vXM[alXfMhMM_g{p`Mi_Z^t[MVSlp_______m__e_____}_________bbbbbbbbbbbbbbbbbbbb}bbbbbbxeeeeeeekeeeeeee" +
        "eeeekeeeeeyqgggogggqgqgggoggggooggolgslfffwffflffpffsffffftfffffpTcbTyTZgTTtgrh`TTv`ZTTZTT`ijbbbpbbblbbbbb|bbb" +
        "bbbbbbbbbccccnccc|cccccocccccccccccckkkkkkkkkkkkkkkkkkkkkkkkkkvVV]dVV]ViVi]lgV]VVVVnVVVVb}rRRRoRRRrRRyRRhRRRXR" +
        "aR]Rq`ulllllllllllllllllllllllllllSjjhSlk`gMPojwUlLnppg`dSVSvsWXGxJGVnPGmSZqS4qPTt<I9r4hu]PUlRBskG@oPYy[9oTPmL" +
        "EF_U^sPXLxEEWuQ?QVXvL<qYOm_]<^@er`jjVicZiURpnuO_fhkcmsQukLfrSWWpCFAt?:mQOyH4uPVm4GJK4]t[MXsMHbq8Bm_VvXAuTpnAUA" +
        "]BbwOJUx?CHvAOKNFsZHaPblED=^<YUb^cTjZEW:GbjzV_DjruF_ENOKsv]VVrYDakQNPPWxb>aRRwVM>J>gpXSVuPXgwLVbdwpX>gVMkVUDe>" +
        "cvQWIv<RHvPJgNSvV7FWohPDQf7_wVbTtLTLt=JXUOv[4l`PoSYEo4era[YxaQ^oTR]QSyP7J][kXQMcVj]feZMzRahH[iXvOlJn]jomgULJcu" +
        "NcUsYXknBQr^SsL;w]YoEI;W5ej`cIFI@@T@@S@LS@IN@d~M`LO@irWPX{WEYqIKFOOsO6IWMpIXCa?]sUjNsKMrnFagf`rn]WLspReCeKrkGH" +
        "NlA>{j<CCQFvE6lU@gXhK[Dg^Z[U]W^TLEib^xEw:ivgCRON:UprCYIwTPSyCMeSWtY=XgReFVQXSjvMHFtGLuwCAKUOsO9gOVREa3Q3Zin`ai" +
        "`VStMMjnScuS[j`^pVsSMvo;DFx;ASa;KYKG{D;PRAgKA;DI_tWYfvTLkuLLZa`xQLWXZlf]L_ZfrmnkiminocckmjolakqsgdnSfU!"

    /** 완성형 한글 음절 11,172자의 로그확률. 인덱스 = 음절코드 − 0xAC00. */
    private const val KO_SYLLABLE_TABLE =
        "yn(&p&!Pe@1)!!!&la_U^n`<,lL@qc&!K!!,]!!!!!!!MH!O9V!!!&&&J7!!:!!!O&!!!!!!+&!)&=!!!&!!<!*!0!!!2!!!!!!!!!!!!!!!!!" +
        "!!pR/!m!!Rc!*!!!(!eW!o7F./!S<5r<!!_!!!Y!1!!!!!G5!QbA&&!!&!mjY!g&!/m!!!!!!!^U!4]s&*(R,(r!!!1!!!)!!!!!!!!(!95,!!" +
        "!!!!xj!!a!&Zj)8!6!!5VZ&c&sQ-&*)!tY!!r!)!Z!&!!!!!N,!0!k!!!&!!N&!!R!0!(!!!!!!!!&!!(I!!!!!!bD!!=!!!?!!!!!!!=&!)!R" +
        "!!!!!!s2!!,!!!2!!!!!!!)&!9!1&!!!!!uw(!p&!U`QL(!!!-GU!W!bC6&*&(Q>!!l!(&V!!!!!!!)!!&:5!!&&!!]&!!@!!!C!!3!!!!&!!(" +
        "!&!!!!!!e6!!R!!&I!!!!!!!A1!F!4&!!!!!h-!!c!&!P!!!!!!!A!!0!(!!!!!!uh!!i!&7lI!!!!&*lj!Q!V(!!1!!;&!!+!!&(!!!!!!!!!" +
        "!!!!!!!!!!zG!!c!!8g!2!!!!!mQ&V1N)!!/[(kKN!P!!)V!*!!!!!OS!D4M*!(S!&]/!!H!!+?!!&!!!!40!?EC!!!!!!<8!!!!!!)!!!!!!!" +
        "!!!&!&!!!!!!2!!!)!!!!!!!!!!!&!!!!!!!!!!!WHT!E!!!D!!!!!!!IS!M>K!!!!!!f)!!6!!!+!!!!!!!-6!F&5!!!!!!R&+!2!!!)!!!!!" +
        "!!!!!/P!!!!.!!8!!!!!!!!!!!!!!!!!!!!!!!!!!!^W+!J!&/U+!!!!!!MT!A!LK^!++!FG!!@!!!4!!+!!!!!!!3(N!!!!!!PA-!&!!!!!!!" +
        "!!!!!!!!!<!!!!!!N!!!0!!!(!!!!!!!7&!!!*!!!!!!9!!!!&!!!!!!!!!!!!!!!)!)!!!!^E(!W!&0S!&!!!!HZJ!F!E8/!&!!Q-!!3!!!(!" +
        "!!!!!!!!!,MH!!!!!!J*!!7!!!-!!!!!!!.)&&*&&&&&&&X/&&O&&&H&&&&&&&J=&(+(&&&&&&;&&&&&&&)&&&&&&&&&&&&&&&&&&&Z)-&U&W&" +
        "^):&&&&OV3&N&=&&&b&&,&&&&&&&&&&&&&&&(&&&&&&&&&&&_B+&Q&&&M&&&&&&&UA&@&:&&&&&&u^O)i&&=jK6)&&&&p[&RbZ_N/R)WoJ&&`&" +
        "&,W&&(&&&&SR&I_Z)&&&&)`F&&H&&&C&&&&&&&G1&5([&&(&&&1)&&(&&&&&&&&&&&,&&&+/&&&&&&iM&EX&)(h*9[&&+,mJ*QCI((++.]mY&&" +
        "Y&&,[&&&&&&&ML(a@D(&,&&)gW*&y(&,7(&(&&&&d3&ESZ&&I)&,P.&&D(&&?&&&&&&&5&&4&1&&&&&&pa(&e&&*^(,)(&&&TL&F&e(&&*d^I&" +
        "&&8&&&)&&&&&&&&&&-I+&&&&&),&&&&&&&&&&&&&&&&&&&&&&&&&&&^+&&-&&(7&&&&&&&,(&(((&&&&&&W<&&@&&&Q&&&&&&&A+&.(N&&(&&&" +
        "gU&&`&&*U&&&&&&&SJ&H&E&&&//)N&&&(&&&*&&&&&&&(&&&C&&&&&&&4&&&&&&&)&&&&&&&&(&(&&&&&&&&X1&&R&&(C&&&&&&&>8&,&(&&&&" +
        "&&h7&&6&&,@&&&&&&&Y.&+&C&&&&(&cT(&z*&.bM*&&&&&H4&2(jV,)*M(U(&&+&&&7&&&&&&&,&&)&)&&&&&(s_(,c&&1]*)&&&&&cW&]-`)*" +
        "))<)|]O&q((YlZR4&&&Bf^&U-p-D7,&RyN&&[&&(R&&&&&&&SL&RDP)&&&))G,&&>&&+3&&&&&&&((&+&1&&&&&&5&&&(&&&&&&&&&&&&&&&&(" +
        "&&&&&&me6&k&&1`&8Q&&&*]J&U-W0I&)W1pT&&c&&)e&&&&&&&PF&J)K&&,(&(H,&&F&&&,&&(&&&&&)&&B5,&&&,*=(&&K&&&,&&&&&&&&&&&" +
        "&&&&&&&&wm&&`&&Pg0/&8&&/WV&R&t+L&5&.:&&&D&&(+&&&&&&&+&&(*/&&&&&&Y)&(:&&(5&&&&&&&,8&/_)(&&&&&s6&&p&&*e&*&&(&&^Z" +
        "&4FH,&(&&&?(&&8&&&*&&&&&&&.(&(&2&&&&&)mZ&&^&&)c&*&&&3)TM&@&_&(&&&(K&&&*&)&*&&&&&&&&&&+M*&&&&&&5&&&&&&&&&&&&&&(" +
        "&,&&&*&&&&&&d8&&<&&)C&&&&&&&=)&X)>&&&&&(b.&&I&&&V&&&&&&&Q&&&&3&&&&&&ug&&h&&Xr(F2&&&)ZU([&o(&&*&)=(&&(&&&&&&&&&" +
        "&&6(&&&)&&&&&&o[&&]&&GX&&&&&&(NR&Q=`O4&)/)iU(&W&&/_(&&&&&&L3&QK[&&&&&Ak/&&K&&&@&&&&&&&H4&4@K&&&&&&0(&&&&&&+&&&" +
        "&&&&+&&+&*&&&&&&>&&&/&&&&&&&&&&&(&&&(4&&&&&&`V)&_(((_(,;&&&(=5&DI:&&&&&ZV3&&B&&&A&&&&&&&41&C<3&&&&&&,&&&(&&&&&" +
        "&&&&&&&&&&++&&&&&&2&&&)&&&)&&&&&&&(&&)&&&&&&&&kU&&A&&&H&&&&)&&>)&<&O&&&&&65*&&&&&&(&&&&&&&&&&&&&&&&&&&2&&&&&&&" +
        "&&&&&(&&&&&&&&&&&.&&6&&&*&&&(&&&&&&&&&&&&&&&&&&&)&&&&&&&&&(&&&&)+&&&&+&&&&&)VQ&&?&&(E&)/&&&Q=)&/&R&&&&&*0&&&,&" +
        "&&&&&&&&&&&&&&&.&&&&&&2&&&&&&&&&&)&&&&&&&&&&&&&&&&^&&&J&&&G&&&&&&&@2&&&(&&&&&&3&&&&&&&&&&&&&&&&&&&&(&&&&&&ZG&&" +
        "N&&LO&&&&&&2MB&b&4&&&(&(Y+&&H&&&;&&&&&&&::&&&&&&&&&&W3&&P&&&>&&&&&&&74&2&E&&&&&(wd&&k&(1]&&&&&&&k^&Q[k+(++<Fma" +
        "&(i&&,O&(&&&&&fY&_N_-&((/&Wa&&J&&*?&&&&&&&59&5*h&&&&&*/&&&,&&&)&&&&&&&(&&&)&&&&&&&p^&&g&))_&&&&&&(ei&V]S(((&._" +
        "rc&&e&&+Z&&&&&&(XO&X7Q&&).(3lm&&h&(,^&&&&&&&W[&Rbj)&&&&)d+&&P&&(.&&&&&&&1,&,&*&&&&&&zm&&m&)4a&&*&&&&^^(`&^*&()" +
        ")/<3&&@&&&)&&&&&&&)&&&&,&&&&&&,&&&(&&&&&&&&&&&&&&&-&&&&&&&_8&&J&&&G&&&(&&&M5&(&C&&&&&(i,&&6&&,@&&&&&&&0>&4&_&&" +
        "(&&&nZ&&]&&*W&&&&&&&Yd&M&V&&)&&&V&&&,&&&1&&&&&&&(&&+L)&&&&&(9)&&+&&&((&&&&&&(5&(&(&&&&&&[J&&H&&&G&&&&&&&B7&:)A" +
        "&&&&&&w`&&X&&(b&&&&&&&X:&4&U&&&((&sQ&&k&&,v)*&&&&&mQ&R&^)&&-Q/8(&&)&&&+&&&&&&&)&&&&&&&&&&(ye&&k&&+b(((&&&&nl(]" +
        "*k((,,+,tg(&q(gMkX)()(&)TM&Y,kb9/`)Ald&&c&&&O&&&&&&&HS&X<cY0(.&&M=&&@&&&1&&&&&&&1(&3&7&)&&&&+&&&/&&&&&&&)&&&&&" +
        "&&&(&&&&&&h_&(f&((]&0&&&&&T:&S)V>,((&6n^&&`&&(_&&&&&&&]>&R0O))&(&&pK&(p(&(_&&&&&&&3&&.Fs7a((((;&&&F&&&0&&&&&&&" +
        "(&&(&&&&&&&&qo(Nc&&5b&(&)&&(^L&e(`&((&(/6&&&+&&&(&&&&&&&)&&&),&&&&&&.&&&&&&&&&&&&&&&&&&&&&&&&&&&P(&&L&&&G&&&&&" +
        "&&-(&8&9&&&&&(b)&&A&&&<&&&&&&&+(&2(5(&&(&&pX`(s&(WoB?&&(&(Q:(N&R(((@&3T&&(P&&(J(&&&(&&(B&>(2&&),&&1&&&*&&&*&&&" +
        "&&&&((&&&)&&&&&&P+&&X&&&P&&&&&&&+(&)&3&&&&&&c&*&=&&(X&(&&&&&O&&(&-&&&(&&a0(&B&&(P&&&&&&)B)&/&6&&&&&&:&&&,&&&*&" +
        "(&&&&&&&&&&&&&&&&&t]&&r&-[g&<&(&&&NB&UH`8k+W,)pk^Bo&&ipa2P)(&0_Z&N,q1,+U&*nl+&a&&0W(&&&&&&YA&P,[)&&H((M0&&@&&*" +
        ":(&&&&&&2(&:&:&&&&&&,0&&&&&&&&&&&&&&&&&&&&&&&&&&nR&&m,&4g&()(&&(hm&Y)MO<&)&)mZ&&c(&?e&&&&&&&I;&V.S&&)*&*V`(&j&" +
        "(&m&&&&&&&+M&G=j&2(G&&=(&&H&&&4&&&&&&&&&&/&&&&&&&&tjN&o&&0f(()&(&&ZV&[&g(&)+()W(&&1&&(10&&&&&&&&&3S*&&(&&(7&&&" +
        "&&&&(&&&&&&&(-&&,&&&&&&&O,&&=&&&G&&&&&&&@D&-.*&&&&&&G&&&2&&&9&&&&&&&&&&,&.&&&&&&uk(&w(&9lX,&&&((R?&S&]*()`((4&" +
        "&&)&&&+&&&&&&&&&&&(.&&((&&8(&&+&&&*:&&&&&&(&&*&(&&&&&&`3&*G&&&H&&*&&&&.&&:&)&&&&&&_)&&E&&&H&&&&&&&L&&,&2&&&&&&" +
        "n5(&]&&*i+&&&(&&.*&,&?&&&*&&7&&&)&&&)&&&&&&&)&&&&&&&&&&&s_&&d&&2e&.&&&&&SD&W&^R^/.)&aJ&&D&&)Z&)&&&&&;1&?EV&&&&" +
        "&?WK(&H&&(E&&&&&&&;3&H@D&&&(&&1-&&&&&&&&&&&&&&I+&+(&&&&&&&3,&&)&&&(&&&&&&&&&&)((&&&&&&OC&&O&&QG&&&&&&&0*&FDA&&" +
        ")+&(B2&&0&&&/&&&&&&&)&&,&0&&&&&(W;&&*&&&&&&&&&&&<)&?(,&&&&&&4)&&&&&&&&&&&&&&&&&&&&&&&&&&O>)&:&&&=&&&&&&&FT&/&K" +
        "&&&&*&+&&&&&&&&&&&&&&&&&&&&&&&&&&&)&&&&&&&&&&&&&&&&&&&&&&&&&&&*&&&&&&&&&&&&&&&&&&&&&&&&&&&L+&&&&&&&&&&&&&&&&&(" +
        "&@&&&&&&W?&&_&&(U&&&&&&&K,&1&C&&&&&&+&&&&&&&&&&&&&&&&&&&&&&&&&&&)-,&&&&&&,&.(&&&&&&&&&&&&&&&)&&&&&&&&&&&&&&&&&" +
        "&&&&&&&&&&:)&&&&&&&&&&&&&&&&&&&(&&&&&&S(&&U&&&F&&&&&&&N?&(&5&&&&&&*(&&&&&&&&&&&&&&&&&&&&&&&&&&P<&&<&&)9&&&(&&&" +
        "4/&.&:&()&&&yb)<q)&2i?Y*&&&)hY&UKt(&,E.*hi+&[,&-S&(&&&&&Z<&L5q(&)&((dG&&T&&(V&&&&&&&PP&R&V&&&(:&S:&&@&&&:&)&&&" +
        "&&F1&,&D&&&&(&xkT)t)&Ep14-&&&(d_([Vt)&*0=(sX&&g&&*b&)&&&(&XW&Y=H&&&(-*a7+&i&&&^&&&&&&&G6&EZB&&&&&(_@&&R&&&X&&&" +
        "&&&&DC&:&H&&&,&(sn;(g&&.a&(&(&&&SJ&P&l&()I/2>(&&:&&&+&&&&&&&&&&*&I&&&&&&^&&&*&&&)&&&&&&&(&&(,)&&&&&&Z(&&J&&&?&" +
        "&&&&&&,+&=&0(&&&&&d9&&R&&&L&&&&&&&?Q&K&H&&(&-&wb&&j&&Ek*&&&&&&]@&X&].G)CY)D&&&,&&&*&&&&&&&(,&&9+&&&&&&U?&&D&&&" +
        "N,&2&&,&10&,&A&&&&(&_:&&M&&&O&&&&&&&LZ&A&6&(&&&&hB8&Q&&&W&&&&&&&S8&N&O&)+&&+z:)&f&&(hN&&&&&)Xj([&m*&315)7&&&,&" +
        "&&,&&&&&&&&&&&&&&&&&(&yo&&r(*Nm*&(&&(Skc&R(f.)+0]&_N&,U&))T&,&&&&(I@&6F^&&&&&VH<&&>&&&+&&&&&&&D*&)*@&&&&&&;&&&" +
        "0&&&0&&&&&&&&&&&&5&&&&&&).&&&&&&&&1&&&&&(&&&&.&&&&&&aL+&S&&&N&(&&&&&QI&:XF&&&&&)L6&&D&&&;&&&&&&&.)&,(,&&&&&&/&" +
        "&&&&&&&&&&&&&&&&&&,&&&&&&&1&(&)&&&(&&&&&&&&&&&&&&&&&&&TE&&K(&OJ&(&&&&&=/&0&L&&&.&)H*&&7&&&,&&&&&&&((&)@.&&&&&+" +
        "M(&&&&&&&&&&&&&&&&&&*(&&&&&&:&&&/&&&*&&&&&&&*)&&&+&&&&&&=&&&(&&&&&&&&&&&&&&&&/&&&&&&TK&&M&&&2&&&&&&&-+&*&N&(&&" +
        "&&:&&&(&&&&&&&&&&&&&&&)&&&&&&&/&&&(&&&&&&&&&&&&&&)&&&&&&&&7&&&+&&&&&&&&&&&&*&)&&&&&(&&0&/&)&&&&&&&&&&&(&&&&-&&" +
        "&&&&h=,&Y&(&W&,(&&&+XJ&/&/&&&)&&M&&&1&&&)&&&&&&&0&&&&&&&&&&&fY&&Y&&+G&&&&&&&<I&L.N&&&&&,wj+&pUk.l2E3&&&Ogc-Wif" +
        "8.01b5jc&&c&&1c&)&&&&&RU&PC])&&(()ok&&]&)(T+.Q&:((NE&I1n&(&N*ES+&&:&&&:&&&&&&&5,&*.;&&&&&&vc/&lM.`dP4&&&&&dnkZ" +
        "rW9(H*K,{]&&h&&,g&&&)&(&[O&U1R&&,&.(ssM)r&&1j,3E&1((b[=Nns))-IW7m>&&X1&&S&(&&&&&CK&[1B&&&&&&sc&)i&&0k:[5.&)QUW" +
        "(W-_+D-(5+rN&)g&&,V&&&(&&&DH&Tbk&&,*&&^:&&G&&&2&(&&&&&<)&.*=&&&&&&m5&&Z&&(C&&&&&&&.*&3,?&&(&&&qc&&S&&(L&&&&&&&" +
        "ND&I)s&(3&&,s`&&n&(0k(2*&(&&eK(^&_()&.)+iR&&t&&*v&)(&&)(M1&>[M(&/(&*hE&&U&&&[&&(&&&&Jg(D&D&(&.&&tR&&b&&+a&&&&&" +
        "&&NE&W,Y(&-+&+sj&&f&&)d&&2&&&&C6&H&_/@&&(&uI6&w((3x*.)&&F,qa,B)e512347|+&&A&&&1(&&&&&&/&&+&B)&(&&,~d3&w+44x[/*" +
        "&&)Zol,ZtcS773Z1xq()a&N/c))0&&&&ac&N@tN)),)(p]&&K&&(?&&&&&&&OA&M8g,*((&&Q6&&B&.&2&&&&&&&10&2)H&&&&&&9&&&0&&&)&" +
        "&&&&&&&&&&&&&&&&&&ms)(v*&0h(Y=&&&(mg&L6vR(&&&&v`&&`&&&`&&&&&&&OG&O&J(&&&*&e4&&I&&&A&&&&&&&/,&4e;)&(&&&=&&&<&&&" +
        "2&&&&&&&)&&-&*&&&&&&ri(&i)(.c&(&&&&(ZT&7&oAE&(+bd-&&8&&&2&&&&&&&()&)&P(&&&&&>&&&(&&&&&&&&&&&&&&&(,&&&&&&a&&&A&" +
        "&&<(&&&&&&4,&7&/&&&&&&[*&&C&&&/&&&&&&&,*&1&6*&(&&*wc(&k)&)c()&&&&&TM&D&r()&&&(S&&&.&&&(&&&&&&&&&&/P)&&&&&&?&&&" +
        "*&&&&&&&&&&&&&&(&)&&&&&&a+&&N&&&N&&&&&&&7*&7&(&&&&&&U+&&J&&&D&&&&&&&,&&3&3&&&&&&mb&&f&&*](&&&&&&_M&6&i,&&&&)3&" +
        "&&(&&&,&&&&&&&*&&&&&&&&&&&ym&(q&(9j&E)&&&&]k&[,eL)+ON(fX&&J(=&I)9Z&&*/FB*:?U&&&&&&f<(&>&(&,&&&&&&&</&=L=,&&&&&" +
        "82&&1&&&&&&&&&&&*&&(&G&&&&&&7&&&&&&&(&&&&&&&*&&*1(&&&&&&SJ&&O&&&M&(&&&&&>D&/*I&&&&&*B)&&3&&&0(&&&&&&+&&-(.&&&&" +
        "&&A&&&&&&)&&&(&&&&&&&(6;&&&&&&1&&&+&&&(&&&&&&&&&&&(&&&&&&&Lg-&?&&*D&&&&&&&;+&2&EAU&&&*19&&6&&&(&&&&&&&&&&&()&&" +
        "&&&&*&&&&&&&&&&&&&&&&&&&(&&&&&&&@&&&2&&&3&&&&&&&,(&&&(&&&&&&1&&&3&&&&&&&&&&&(&&&&.(&&&&&MP(&A&&&D&&&&&&&6C&&&H" +
        "&&&&&&I&&&&&&&&&&&&&&&&&&&-,&&&&&&,&&&&&&&&&&(&(&&&&&&&&&&&&&&2&&&6&&&&&&&&&&&&&&&&&&&&&&&=.&&&&&&(&&&&&&&&&&&" +
        "&&&&&&&&T:&&.&&&(&&&&&&&Q(&9&A&&&&&&*(&&,&&&&&&&(&&&&&&&&&&&&&&&XY*&O&&+N&&(&&&&H;&>&AL,&(&>od(&d&S+e,&&&&&)kI" +
        "&KFib6&&,)gh&&O&&&T&&&&&&&aJ&MAN&&&*&&M1&&C&3&6&&&&&&&52&.&;&&&&&&+&&&&&&&&&&&&&&&(&&&&&&&&&&&kb&&m&(*k&&&&&&&" +
        "Z[&e2m.&(&&(oJ&&X&&(Z&&&&&&&CE&I&G(&*&(&b0&&;&&//&&&&&&&,)&/];&&&&&(;&&&;&&&)&&&&&&&&&&&&(&&&&&&m_&&g&&&P&&&&&" +
        "&&K?&M&j*&&&(&8-&&Q&&&`&&&&&&&-&&(&<&&&&&&/&&(&&&&&&&&&&&&&&&&&&&&&&&&m/&&/&&(6&&&&&&&,)&(&.&&&&&(P-&&1&&&)&&&" +
        "&&&&04&2&5&&&&&&kn&&a&&,q((&(&(+XF&F&h(*((&&V&&&.&&&,&&&&&&&&&&(M,&&&&&&O.&&8&&&*&&&(&&&&&&*&+&&(&&&f-&&G&&&5&" +
        "&&&&&&(*&,&1&&&&&&S/&&@&&&7&&&&&&&0.&(&2&&&&&&je&&F&&(N*&&&&&&I)&<&e&&(&)&3&&&(&&&&&&&&&&&&&&&&&&&&&&&r`&&f&&(" +
        "a@)&&&&(eV&L&f*+)(.)rN&&d&&)d&&&&&&&[R&M&U&&),)(g@&&[&&&^&&&&&&&^W&R4L&&&&1&M,&&@&&&9&&&&&&&0)&*(6&&0&&&0&&&&&" +
        "&&&&&&&&&&&&&&(&&&&&&&h?&&a&&Pc&&&&&&&cf&[NH(&+*)&jC&&^&&(]&&&&&&&OC&^)F&&*)&&^(&&D&&&G&&&((&&3-&1_/&&(&&&8&&&" +
        "*&&&0&&&&&&&&&&&&&&&&&&&pW&&e(&,d&&&&&5&]V&W&a(&8,)&W3&&L&&&F&&&&&&&7(&:&C&&&&&&W5&&&&&&)&&&&&&&&&&&&B&&&&&&P6" +
        "&&5&&&Q&&&&&&&)+&(&,&&&&&&b)&&>&&&6&&&&&&&&&&(&8&&(&&&kQ&&U&&&[(&&&(&&NH&C*O&&&*(&X9&&P&&&P&&&&&&&40&,(8&&&&&&" +
        "Y3&&A(&&=&&&&&&&()&/&0&&&&&&ZL&&Y&&&N&&&&&&&<B&;&<&&(&(&`(&&J&&&P&&&&&&&5*&.&=&&(&&&r:&&c&&&k&(&&(&(ZC&-&=(&0&" +
        "&&-(&&&&&&&&&&&&&&&&&&&&&&&&&&pT&&c&&(_&&&&&&&WO&W&a&&/))&r`&&h&&,hG&&&&&&``&RK_&&*0&(me&)Y&()S&&&&&&&RQ&I@X(&" +
        "&&)&J(&&?&&&0&&&&&&&)+&&&+&&&&&&4&&&&&&&&&&&&&&&&&&&&&&&&&&)qS&&f&&&b&)&&&&&UG&H<N&&&,&)l_&&b&&*h&&&&&&&cQ&L&L" +
        "&&*+,&B(&&1&&&,&&&&&&&(&&(A)&&&&&&E&&&T&&&)&&&&&&&&(&&&)&&&&&&rW(&a&&(`&&&&&&)[Z&E(o(&)+5(0&&&>&&&&&&&&&&&+&&&" +
        "&2&&&&&&<&&&&&&&&&&&&&&&&&&&&)&&&&&&c/&&>&&)=&&&&&&&,(&A&;&&&&&(@)&&+&&&*&&&&&&&B+&(&.&&&&(&lK&&X(&(W&&&&&&&S@" +
        "&7&U&&&&(&D&&&&&&&*&&&&&&&&&&&91&&&&&&:&&&&&&&(&&&&&&&&&&(&&&&&&&&_1&&C&&&C&&&&&&&<)&,&F&&&&&&a/&&M&&&G&&&&&&&" +
        "R2&&&1&&&&&&ul&(_1&Ei&8&&&&&R@(B&A&&&4)(?&&&)&&&)&&&&&&&*)&&&(&&&&&&n_&&b&&&^&&&&&&&lO&D&b&&*,(&pQJ&l(&+d&+&&&" +
        "&&T[&TBW&&,M+(h[&&_&&)S&&&&&&&NF&O4Y&&&(&&D0&&8&&&5&&&&&&&&&&(&/&&&&&&0&&&&&&&&&&&&&&&&&&&&&&&&&&&iN&&X&&&X.&&" +
        "&&&&SJ&C8X&&&)&&lX&&a&&&^&(&&&&&KO&S&Q&&&)&(T(&&k&&&W&&&&&&&J/&(Fk&&&&&(f&&&1&&&/&&&&&&&&(&(&&&&&&&&qe&&b&&(f&" +
        "(&&&&&^?&F&U&(&1&&4)&&.&&&)(&&&&&&&&&&&3&&&&&&*&&&&&&&&&&&&&&&&&&(&&&&&&&&I4&&<&&&F&&&&&&&&)&&&4&&&&&&p/&&4&&)" +
        "7&&&&&&&&)&9&6&&&&&&dO&&S&&*a&6&&&(&j6&Y&d&&&)&(0&&&&&&&)&&&&&&&&&&&&(&&&&&&4&&&*&&&(&&&&&&&&&&&&&&&&&&&O&&&<&" +
        "&&A&&&&&&&)&&,&(&&&&&&a,&&0&&&N&&&&&&&H&&)&.&(&&&*r+&&]&&&j&&&&&&&Q:&3&7&()*&&,&&&&&&&&&&&&&&&(&&&&&&&&&&&ni&&" +
        "b&&&i&&&&&&&LQ&Q&_&))*)(ys+(x+(.n.))&E(+mn&W4l/(-*,6ta,&Z&&0O&&((&(&ZF&Qro*,(&&;H4&&7&&&?&(&&.&&1-&;/i&&&&&&3&" +
        "&&&&&&(&&&&&&&&&&)6(&&&&&&fG&(e&&(T**&&&&&f@&O+[&&(&&2fR&&a&&(_&&&&&&(NI&I4K&&&&&6_d&&p)&&_&&&&&&&Yg&>]m&&&((&" +
        "b&&&1&&&:&&&&&&&2)&(&(&&&&&&q`&&e&&-b&&&(.&*cU&Y&f(&&F(,th&&i&&(j&&&&&&(-.&?&j)&&&&+?8(((&&&&(&&&&&&&&&H*+&&&&" +
        "&&se&&7&&+B&&&&&&&&-&Z)W&&&(&&e,&)?&(&.&&&&&&&&)&3&5&&&&()pK&&g64&U/&&&B&2S9&T&Q&&-3)(F/&&K.&&X&&&&&&&)&&&&8&&" +
        "&&&&[0&&6&&&3&&&&&&&*&&.&5&&&&&&b:&&C&&(T&&&&&&&?O&C&3&&&&&&a+&&5)&&F&&&&&&.G&&)&V&&&&&&d^&&^*)3YS&&&.&&R[&/&c" +
        "&&)P&<g5&&X&&&0&&&&&&&,/&(&5&&&&&&mK&&^&&(])&&&&&&aV&H,J&&&&)6"

    /** 테이블 문자 → 양자화 단계(0..LEVELS-1). 코드포인트 33..126 에서 `"` `\` `$` 를 건너뛴 순번. */
    private val DECODE_STEP = IntArray(127).also { arr ->
        var step = 0
        for (code in 33..126) {
            val ch = code.toChar()
            if (ch == '"' || ch == '\\' || ch == '$') continue
            arr[code] = step
            step++
        }
    }

    private fun decode(tableChar: Char, lo: Double, hi: Double): Double {
        val frac = DECODE_STEP[tableChar.code].toDouble() / (LEVELS - 1)
        return lo + frac * (hi - lo)
    }

    private fun symbolIndex(ch: Char): Int = if (ch in 'a'..'z') ch - 'a' else 26

    // ─────────────────────────────────────────────────────────────────────
    // 토큰 판정(2026-09 재설계) — 가설별 맞대결
    // ─────────────────────────────────────────────────────────────────────

    /** [judge] 결과. */
    class Judgement(
        /** 최종 신뢰도(0~1) — 설정의 "신뢰도 임계값(%)"과 그대로 비교한다. */
        val confidence: Float,
        /** CapsLock 을 켠 채 친 한영타라는 가설이 이겼는가 — 교체 때 대소문자를 뒤집어 변환한다. */
        val capsLock: Boolean,
    )

    /**
     * 라틴 토큰 하나가 한영타일 신뢰도. 두 "한영타 가설"을 각각 여러 "대안 설명"과 맞붙여,
     * 가설마다 **가장 강한 반론을 상한**으로 삼고(min) 두 가설 중 나은 쪽을 쓴다(max).
     *
     * 한영타 가설
     * - **CapsLock 꺼짐**: 원문 그대로 두벌식 변환.
     * - **CapsLock 켜짐**(대문자가 과반일 때만): 대소문자를 뒤집어 변환(`DKSSUD`→안녕,
     *   `GOtEK`→했다 — CapsLock 중 Shift 를 누르면 소문자가 나오므로 소문자 t 가 ㅆ).
     *
     * 대안 설명(각각 로그오즈로 비교)
     * 1. **영어 단어**: 기존 글자당 점수([confidence] 척도) — `(한국어 − 영어) / 글자 수`.
     * 2. **한국어 글 속 알려진 라틴 문자열**([lexiconLogProb], "Shift 증인" 사전): `sns`/`dlc`/`fps`.
     * 3. **약어**([acronymLogProb]): 사전에 없는 약어·모델명(`cmd`, `SBTi`)의 일반화.
     *
     * **Shift 물리**: 두벌식에서 Shift 가 의미 있는 키는 Q·W·E·R·T·O·P(쌍자음·ㅒㅖ) 뿐이다. 그 외
     * 키의 대문자(`sNl` 의 N, `CoA` 의 A)는 한글을 치던 사람이 굳이 누를 이유가 없으니 **반대 증거**
     * 로 벌점([NEEDLESS_SHIFT_PENALTY]), Q·W·E·R·T·O·P 대문자만 있으면 가산([SHIFT_BONUS]).
     * CapsLock 가설에선 같은 규칙을 소문자에 적용한다. (예전엔 첫 글자 외 대문자면 무조건 +2.0 이라
     * `sNl`→89.7%, `CoA`/`QoS`/`GPa` 같은 과학 약어가 줄줄이 오탐이었다.)
     *
     * [koreanContext] = 선택 주변에 한글이 있다(한국어 문서). 그러면 "영어 단어" 가설의 사전확률만
     * [CONTEXT_LOG_ODDS] 만큼 낮춘다 — 약어·알려진 라틴 문자열은 원래 한국어 글에서 센 값이라 그대로.
     *
     * 실측 근거와 수치는 docs/한영타_검증.md.
     */
    fun judge(text: String, letters: String, mappable: Int, allUpper: Boolean, koreanContext: Boolean): Judgement {
        val n = letters.length
        if (n == 0) return NO_JUDGEMENT
        // 라틴(ASCII) 글자의 대소문자 모양
        var latin = 0
        var upper = 0
        var needlessOff = 0 // CapsLock 꺼짐 가설에서 의미 없는 Shift(첫 글자 제외, Q·W·E·R·T·O·P 외 대문자)
        var needlessOn = 0  // CapsLock 켜짐 가설에서 의미 없는 Shift(Q·W·E·R·T·O·P 외 소문자)
        var shiftedInner = false
        var shiftedLower = false
        var firstUpper = false
        for (c in text) {
            val isUpper = c in 'A'..'Z'
            if (!isUpper && c !in 'a'..'z') continue
            val shiftKey = c.lowercaseChar() in SHIFT_KEYS
            if (isUpper) {
                if (latin == 0) firstUpper = true
                else if (shiftKey) shiftedInner = true
                else needlessOff++
                upper++
            } else if (shiftKey) {
                shiftedLower = true
            } else {
                needlessOn++
            }
            latin++
        }
        val caseLog = when {
            upper == 0 -> ACRONYM_CASE_LOWER
            upper == latin -> ACRONYM_CASE_UPPER
            firstUpper && upper == 1 -> ACRONYM_CASE_TITLE
            else -> ACRONYM_CASE_MIXED
        }
        val acronym = caseLog + acronymLogProb(letters)
        val context = if (koreanContext) CONTEXT_LOG_ODDS else 0.0
        val lexLower = lexiconLogProb(text, upper = false)
        var best = Double.NEGATIVE_INFINITY
        var bestCaps = false

        if (!allUpper) {
            koreanWordLogProb(HangulConverter.convertEngToKor(text))?.let { ko ->
                val shift = when {
                    needlessOff > 0 -> -NEEDLESS_SHIFT_PENALTY * needlessOff
                    shiftedInner -> SHIFT_BONUS
                    else -> 0.0
                }
                var z = wordLogit((ko - englishLogProb(letters) + context) / n + shift)
                if (lexLower != null) z = minOf(z, ko - lexLower)
                z = minOf(z, ko - acronym - NEEDLESS_SHIFT_PENALTY * needlessOff)
                if (z > best) best = z
            }
        }
        if (upper * 2 > latin) {
            koreanWordLogProb(HangulConverter.convertEngToKor(swapCase(text)))?.let { ko ->
                val shift = when {
                    needlessOn > 0 -> -NEEDLESS_SHIFT_PENALTY * needlessOn
                    shiftedLower -> SHIFT_BONUS
                    else -> 0.0
                }
                val english = if (allUpper) {
                    logAddExp(LOG_SHOUTED_WORD + englishLogProb(letters), LOG_ACRONYM + acronymLogProb(letters))
                } else {
                    englishLogProb(letters)
                }
                val short = if (n <= 3) CAPS_SHORT_LOG_ODDS else 0.0
                val prior = CAPS_PRIOR + short
                var z = wordLogit((ko - english + context) / n + shift + CAPS_WORD_BONUS) + short
                lexiconLogProb(text, upper = true)?.let { z = minOf(z, prior + ko - it) }
                z = minOf(z, prior + ko - acronym - NEEDLESS_SHIFT_PENALTY * needlessOn)
                if (z > best) {
                    best = z
                    bestCaps = true
                }
            }
        }
        acronymTailLogit(text, lexLower)?.let { z ->
            if (z > best) {
                best = z
                bestCaps = false
            }
        }
        if (best == Double.NEGATIVE_INFINITY) return NO_JUDGEMENT
        val conf = 1.0 / (1.0 + Math.exp(-(best - CALIBRATION_SHIFT))) * mappable / n
        return Judgement(conf.toFloat(), bestCaps)
    }

    private val NO_JUDGEMENT = Judgement(0f, false)

    /**
     * "대문자 약어 + 영타 꼬리"(`GUIdml`=GUI의, `CGrk`=CG가). 토큰 통째로 보면 약어까지 한글로
     * 변환돼(`혀ㅑ의`) 점수가 깎이므로 꼬리만 재고, 약어 바로 뒤에 소문자가 붙은 구조 자체를
     * [ACRONYM_TAIL_BONUS] 로 더한다. 꼬리가 2글자면 정보가 한 음절뿐이라 흔한 조사·어미
     * ([AFTER_ACRONYM_PARTICLES])로 변환될 때만 인정한다 — 영어 쪽 오탐 후보(`CNNfn`→루, `WEek`→다,
     * `NDak`→마)가 전부 2글자 꼬리였다(실측: 영어 1,452회 중 오탐 0, NSMC 약어+조사 94.9% 감지).
     */
    private fun acronymTailLogit(token: String, lexLower: Double?): Double? {
        val text = token.trim { it !in 'a'..'z' && it !in 'A'..'Z' } // 문장 끝 마침표·괄호(`GUIdml.`)
        var i = 0
        while (i < text.length && text[i] in 'A'..'Z') i++
        if (i < 2 || i == text.length) return null
        val tail = text.substring(i)
        // 꼬리 안의 Q·W·E·R·T·O·P 대문자는 쌍자음·ㅒㅖ 라 정상이다(`SFdudghkrPdml`=SF영화계의).
        if (tail.length < 2 || !tail.all { it in 'a'..'z' || it in SHIFT_KEYS_UPPER }) return null
        val tailKo = HangulConverter.convertEngToKor(tail)
        if (tail.length == 2 && tailKo !in AFTER_ACRONYM_PARTICLES) return null
        val ko = koreanSuffixLogProb(tailKo) ?: return null
        var z = wordLogit((ko - englishLogProb(tail.lowercase())) / tail.length + ACRONYM_TAIL_BONUS)
        if (lexLower != null) {
            koreanWordLogProb(HangulConverter.convertEngToKor(token))?.let { whole -> z = minOf(z, whole - lexLower) }
        }
        return z
    }

    /** 대문자 약어 바로 뒤에 소문자가 붙는 구조 자체의 가산점(실측으로 오탐 0 을 유지하는 보수적 값). */
    private const val ACRONYM_TAIL_BONUS = 1.0

    /** 약어 뒤 2글자 꼬리를 한영타로 인정하는 한 음절(조사·어미). NSMC 실측 빈도 상위 기준. */
    private val AFTER_ACRONYM_PARTICLES = setOf(
        "이", "가", "도", "에", "로", "나", "만", "랑", "야", "요", "고", "지", "게", "서",
        "는", "은", "를", "을", "의", "와", "과",
    )

    /** 두벌식에서 Shift 가 의미 있는 키(쌍자음 ㅃㅉㄸㄲㅆ · 복합모음 ㅒㅖ). */
    private const val SHIFT_KEYS = "qwertop"
    private const val SHIFT_KEYS_UPPER = "QWERTOP"

    /** 의미 없는 Shift 1개의 벌점(글자당 점수 단위 / 약어 비교에선 로그오즈). 실측으로 정함. */
    private const val NEEDLESS_SHIFT_PENALTY = 3.0

    /** Q·W·E·R·T·O·P 에만 대문자가 있을 때의 가산점(글자당). 예전 +2.0 은 과했다(오탐 증가, 감지 동일). */
    private const val SHIFT_BONUS = 1.0

    /**
     * CapsLock 가설의 글자당 보정. 전부 대문자 토큰의 영어 쪽은 "약어" 부류가 주도하는데, 약어
     * 모델은 글자당 확률이 후해(글자당 약 -4, 영어 단어 모델은 비단어에 -9 안팎) 같은 척도로 두면
     * 진짜 CapsLock 한영타도 못 넘는다. 실측: CapsLock 한영타는 한글 변환이 글자당 약 -3, 진짜
     * 대문자 약어는 약 -20(대부분 깨진 자모)이라 둘 사이 간격이 매우 넓다.
     */
    private const val CAPS_WORD_BONUS = 3.0

    /** CapsLock 가설의 사전 로그오즈(평소 입력 대비 약 5%) — 사전·약어 비교에 쓴다. */
    private const val CAPS_PRIOR = -3.0

    /**
     * 3글자 이하 CapsLock 가설 추가 벌점. 한국어 글 속 3글자 라틴 토큰의 약 70%가 전부 대문자
     * 약어(DLC/FPS/SNS…)라, 3글자 대문자를 뒤집어 보면 오탐이 쏟아진다(뒤집기만 하면 10%).
     */
    private const val CAPS_SHORT_LOG_ODDS = -2.0

    /** 전부 대문자 토큰이 "대문자로 쓴 일반 단어"일 몫 / "약어"일 몫(로그). */
    private val LOG_SHOUTED_WORD = Math.log(0.3)
    private val LOG_ACRONYM = Math.log(0.7)

    /** 약어 부류가 그 대소문자 모양으로 쓰일 로그확률 — 약어는 대부분 전부 대문자, 소문자·첫 대문자는 드묾. */
    private const val ACRONYM_CASE_LOWER = -4.0
    private const val ACRONYM_CASE_UPPER = -0.5
    private const val ACRONYM_CASE_MIXED = -3.0
    private const val ACRONYM_CASE_TITLE = -4.0

    /** 선택 주변에 한글이 있을 때 "영어 단어" 가설에 주는 불리함(총 로그오즈). */
    private const val CONTEXT_LOG_ODDS = 3.0

    /**
     * 최종 보정: 기존 영어 오탐 수준(영어 사전 47만 단어 중 102개)에서의 판정 경계가 설정 기본값
     * 70% 에 오도록 로짓을 민다 — 사용자가 보는 "70%"의 의미(오탐 수준)를 예전과 같게 유지.
     */
    private const val CALIBRATION_SHIFT = 0.225

    /** 글자당 점수 → 로짓(= [confidence] 의 로지스틱 안쪽). */
    private fun wordLogit(score: Double): Double = (score - CENTER) / SCALE

    private fun logAddExp(a: Double, b: Double): Double {
        val hi = maxOf(a, b)
        return hi + Math.log(Math.exp(a - hi) + Math.exp(b - hi))
    }

    /** 대소문자 뒤집기(CapsLock 가설). */
    fun swapCase(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c.isUpperCase()) c.lowercaseChar() else if (c.isLowerCase()) c.uppercaseChar() else c)
        return sb.toString()
    }

    // ── 한국어 쪽: 어절 위치별 음절 모델 + 자주 쓰는 어절 기억([TypoTables]) ──────────────

    /**
     * 두벌식 변환 결과가 한국어 **어절**로 나올 로그확률. 한글이 전혀 없으면 null.
     *
     * 예전 음절 unigram 은 음절이 어디에 있든 같은 확률을 줘서 "홀로 쓰이는 흔한 말"(왜/좀/난)과
     * "단어로서 흔한 말"(너무/그냥/내가)을 몰랐다 — 실측에서 가장 많이 놓친 한영타가 드문 단어가
     * 아니라 `sjan`(너무, NSMC 5만 문장에서 2,803회 누락)·`rmsid`(그냥)·`dho`(왜)였다.
     * 이제 한글 구간마다 길이 확률 × 음절별 위치(홀로/첫/가운데/끝) 확률을 곱하고, 변환 결과가
     * 자주 쓰는 어절이면 그 실제 빈도와 반반 섞는다. 조합 실패한 낱자모는 [KO_FLOOR].
     */
    fun koreanWordLogProb(converted: String): Double? {
        var total = 0.0
        var units = 0
        var runs = 0
        var runStart = -1
        var runEnd = -1
        var allSyllables = true
        var i = 0
        val len = converted.length
        while (i < len) {
            if (!isHangulUnit(converted[i])) {
                i++
                continue
            }
            var j = i
            while (j < len && isHangulUnit(converted[j])) j++
            val k = j - i
            total += TypoTables.POS_LEN[minOf(k, POS_LEN_MAX) - 1]
            for (p in i until j) {
                val c = converted[p]
                if (c.code in HANGUL_FIRST..HANGUL_LAST) {
                    val pos = when {
                        k == 1 -> POS_SINGLE
                        p == i -> POS_FIRST
                        p == j - 1 -> POS_LAST
                        else -> POS_MID
                    }
                    total += positionalLogProb(c, pos)
                } else {
                    total += KO_FLOOR
                    allSyllables = false
                }
            }
            units += k
            runs++
            runStart = i
            runEnd = j
            i = j
        }
        if (units == 0) return null
        if (runs == 1 && allSyllables) {
            eojeolLogProb(converted.substring(runStart, runEnd))?.let { eojeol ->
                return logAddExp(LOG_HALF + total, LOG_HALF + eojeol)
            }
        }
        return total
    }

    /**
     * 약어 뒤 꼬리(`CG`+`가`, `SF`+`영화계의`)의 로그확률 — 꼬리는 약어에서 시작한 어절의 **이어지는
     * 부분**이라 음절을 가운데…끝 위치로 채점하고 길이 확률은 넣지 않는다. [koreanWordLogProb] 로
     * 재면 조사 한 음절(`가`)을 "홀로 쓰인 어절"로 봐 확률이 바닥이 된다(실측: 약어+조사 감지
     * 94.9%→91.0% 로 떨어졌던 원인).
     */
    private fun koreanSuffixLogProb(converted: String): Double? {
        var last = -1
        for (idx in converted.indices) if (isHangulUnit(converted[idx])) last = idx
        if (last < 0) return null
        var total = 0.0
        for (idx in 0..last) {
            val c = converted[idx]
            if (!isHangulUnit(c)) continue
            total += if (c.code in HANGUL_FIRST..HANGUL_LAST) {
                positionalLogProb(c, if (idx == last) POS_LAST else POS_MID)
            } else {
                KO_FLOOR
            }
        }
        return total
    }

    private fun isHangulUnit(c: Char): Boolean =
        c.code in HANGUL_FIRST..HANGUL_LAST || c.code in JAMO_FIRST..JAMO_LAST

    private const val POS_SINGLE = 0
    private const val POS_FIRST = 1
    private const val POS_MID = 2
    private const val POS_LAST = 3
    private const val POS_LEN_MAX = 12
    private val LOG_HALF = Math.log(0.5)

    /** 음절 [c] 가 어절 안 위치 [pos] 에 올 로그확률. 학습 글에 없던 음절은 위치별 바닥값. */
    private fun positionalLogProb(c: Char, pos: Int): Double {
        val syl = TypoTables.POS_SYLLABLES
        var lo = 0
        var hi = syl.length - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val m = syl[mid]
            when {
                m < c -> lo = mid + 1
                m > c -> hi = mid - 1
                else -> return decode(TypoTables.POS_TABLE[pos * syl.length + mid], TypoTables.POS_LO, TypoTables.POS_HI)
            }
        }
        return TypoTables.POS_FLOOR[pos]
    }

    /** 자주 쓰는 어절(상위 1천 개)의 실제 빈도. 처음 쓸 때 한 번만 푼다(첫 선택 전까지 메모리 0). */
    private val eojeols: Map<String, Double> by lazy {
        val words = TypoTables.EOJ_WORDS.split(',')
        HashMap<String, Double>(words.size * 2).also { map ->
            words.forEachIndexed { idx, w -> map[w] = decode(TypoTables.EOJ_LEVELS[idx], TypoTables.EOJ_LO, TypoTables.EOJ_HI) }
        }
    }

    private fun eojeolLogProb(w: String): Double? = eojeols[w]

    // ── 라틴 쪽: Shift 증인 사전 + 약어 모델 ─────────────────────────────────────

    /**
     * "Shift 증인" 사전 — 한국어 글에서 **Shift 가 무의미한 키에 대문자가 있는 모양**으로 쓰인
     * 적이 있는 라틴 문자열(`SNS`, `DLC`, `tvN`)은 한글을 치다 생길 수 없으므로 진짜 라틴 문자열이다.
     * 이 원리로 라벨 없이 한국어 원문(뉴스·위키·리뷰 등 학습 분할의 라틴 토큰 77만 회)에서 자동 채굴했고, 그중
     * 판정에 영향을 주는 643개만 담았다. 값은 한국어 글 속 라틴 토큰 가운데 그 문자열의 비율:
     * 소문자는 (소문자 출현 + 0.2×대문자 증인), 대문자는 전부 대문자 출현.
     */
    private class LexEntry(val lower: Double, val upper: Double)

    private val lexicon: Map<String, LexEntry> by lazy {
        val words = TypoTables.LEX_WORDS.split(',')
        HashMap<String, LexEntry>(words.size * 2).also { map ->
            words.forEachIndexed { idx, w ->
                val u = TypoTables.LEX_UPPER[idx]
                map[w] = LexEntry(
                    decode(TypoTables.LEX_LOWER[idx], TypoTables.LEX_LO, TypoTables.LEX_HI),
                    if (u == ' ') Double.NaN else decode(u, TypoTables.LEX_LO, TypoTables.LEX_HI),
                )
            }
        }
    }

    /**
     * [text] 의 라틴 연속 구간이 **전부** 증인 사전에 있으면 그 로그확률 합, 하나라도 없으면 null.
     * [upper] = 전부 대문자 형태의 빈도(CapsLock 가설용).
     */
    fun lexiconLogProb(text: String, upper: Boolean): Double? {
        var total = 0.0
        var any = false
        var i = 0
        while (i < text.length) {
            if (!isAsciiLetter(text[i])) {
                i++
                continue
            }
            var j = i
            while (j < text.length && isAsciiLetter(text[j])) j++
            val e = lexicon[text.substring(i, j).lowercase()] ?: return null
            val v = if (upper) e.upper else e.lower
            if (v.isNaN()) return null
            total += v
            any = true
            i = j
        }
        return if (any) total else null
    }

    private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /**
     * 약어 글자 bigram 로그확률(소문자 [latin]). AG News + 한국어 글의 전부 대문자 라틴 구간
     * 2.5만 종류에서 종류당 1회로 셌다(한 약어가 수천 번 나와도 통계를 왜곡하지 않게). 실측으로
     * trigram·unigram 과 성능이 같아 가장 작은 bigram(729칸)을 쓴다.
     */
    fun acronymLogProb(latin: String): Double {
        var total = 0.0
        var prev = 26
        for (i in 0..latin.length) {
            val cur = if (i < latin.length) symbolIndex(latin[i]) else 26
            total += decode(TypoTables.ACR_BIGRAM[prev * 27 + cur], TypoTables.ACR_LO, TypoTables.ACR_HI)
            prev = cur
        }
        return total
    }

    /**
     * 음절 unigram([KO_SYLLABLE_TABLE]) 기반 구어체 모델 — 낱자모를 [KO_FLOOR] 대신 실제 구어체
     * 빈도로 본다. **교체 문자열을 정할 때만** 쓴다([HangulConverter] 의 문맥 변환). 감지는
     * [koreanWordLogProb](어절 위치별 모델)가 맡는다 — 둘은 목적이 다르다(감지는 "한국어 어절로
     * 흔한가", 교체는 "ㅋㅋ/ㅠㅠ 같은 구어체 낱자모까지 한글로 볼 것인가").
     *
     * 왜 필요한가: 정제된 글에는 `ㅋㅋ`/`ㅠㅠ`/`ㅡㅡ` 가 없어 모든 낱자모가 최저 확률이 되는데,
     * 실제 한영타 문장에는 이게 흔하다(`zzz`→ㅋㅋㅋ). 이걸 최저값으로 두면 이미 한영타로 판정된
     * 선택 안에서도 `zzz`/`bb` 만 영어로 남는 반쪽 교체가 된다(NSMC 대량 검증에서 교체 실패의
     * 대부분이 이것이었다). 반대로 `cpu`→`체ㅕ` 의 `ㅕ` 같은 낱모음은 구어체에서도 드물어
     * 여전히 낮은 확률을 받는다.
     */
    fun koreanInformalLogProb(converted: String, afterLatin: Boolean = false): Double? =
        koreanInformal(converted, afterLatin)?.logProb

    /** [koreanInformal] 결과 — 합계 로그확률과, 그중 가장 드문 전이 하나의 로그확률. */
    class InformalScore(val logProb: Double, val rarestTransition: Double)

    /**
     * [koreanInformalLogProb] + "가장 드문 전이". 합계만 보면 흔한 음절 몇 개가 드문 전이 하나를
     * 가려 버린다 — `cpu`→`체ㅕ` 는 `체` 가 흔해서 합계는 그럴듯하지만 "음절 뒤 낱모음 ㅕ" 는 실제
     * 구어체에서 3만 단위에 한 번꼴(-12.7)이다. 호출부는 이 값으로 "문맥이 구제할 수 없는 기형"을
     * 가른다([HangulConverter] 의 문맥 변환).
     */
    fun koreanInformal(converted: String, afterLatin: Boolean = false): InformalScore? {
        var total = 0.0
        var rarest = 0.0
        var units = 0
        var prev = ROW_BOS
        for (ch in converted) {
            val code = ch.code
            val col = when {
                code in HANGUL_FIRST..HANGUL_LAST -> {
                    total += decode(KO_SYLLABLE_TABLE[code - HANGUL_FIRST], KO_LO, KO_HI)
                    COL_SYLLABLE
                }
                code in UNIT_JAMO_FIRST..UNIT_JAMO_LAST -> COL_JAMO + (code - UNIT_JAMO_FIRST)
                else -> {
                    // 한글이 아닌 문자 = 단어 경계
                    if (prev != ROW_BOS) total += transition(prev, COL_EOS).also { if (it < rarest) rarest = it }
                    prev = ROW_BOS
                    continue
                }
            }
            if (afterLatin && units == 0) {
                // 영어 단어 바로 뒤 첫 단위: 실측 분포(조사 위주)로 바꿔 끼운다([AFTER_LATIN_UNITS]).
                // 위에서 더한 음절 확률은 일반 분포 몫이라 되돌리고 전체를 다시 계산한다.
                val emission = if (col == COL_SYLLABLE) decode(KO_SYLLABLE_TABLE[code - HANGUL_FIRST], KO_LO, KO_HI) else 0.0
                total += afterLatinLogProb(ch, emission + transition(ROW_BOS, col)) - emission
            } else {
                val t = transition(prev, col)
                total += t
                if (t < rarest) rarest = t
            }
            units++
            prev = if (col == COL_SYLLABLE) ROW_SYLLABLE else ROW_JAMO + (code - UNIT_JAMO_FIRST)
        }
        if (prev != ROW_BOS) total += transition(prev, COL_EOS).also { if (it < rarest) rarest = it }
        return if (units == 0) null else InformalScore(total, rarest)
    }

    /**
     * 라틴 글자 바로 뒤(공백 없이)에 오는 첫 한글 단위의 로그확률 — 실측 빈도를 일반 분포
     * ([general], 같은 단위가 단어 첫머리에 올 확률) 쪽으로 평활한 값.
     */
    private fun afterLatinLogProb(ch: Char, general: Double): Double {
        val idx = AFTER_LATIN_UNITS.indexOf(ch)
        val count = if (idx >= 0) AFTER_LATIN_COUNTS[idx].toDouble() else 0.0
        return Math.log((count + AFTER_LATIN_SMOOTHING * Math.exp(general)) / (AFTER_LATIN_TOTAL + AFTER_LATIN_SMOOTHING))
    }

    /**
     * 영어 단어 바로 뒤에 붙는 한글 첫 단위(NSMC train 에서 라틴 연속 구간 13,342개 중 한글이 바로
     * 붙은 5,621건, 5회 이상만). 조사·접사가 압도적이다(`B급`/`cg에`/`ost가`/`3D로`/`sf영화`…).
     * 이 분포가 "영어 단어 + 조사" 쪼개기(`cpusms`→`cpu는`)의 핵심 근거다 — 영어 뒤에 거의 안
     * 오는 음절로 시작하는 쪼개기(`and`+`클한`, `s`+`아`(sdk), `moni`+`색`)는 자연히 밀린다.
     */
    private const val AFTER_LATIN_UNITS =
        "급에가로기도는를영이같의창들만와나라보인드판아하은다시까있전을님방때물용한수랑사스ㅋ자서소야쓰년애발으과지처새무부중액없채네세등작특형문대모적임였배효정게여그장재평해노걸최티출줄개명욕치코진짱점고감프끼단안제바팝냐맨력속입리뿐예성왜너역니버살요마파병연좀타"
    private val AFTER_LATIN_COUNTS = intArrayOf(
        737, 466, 294, 264, 260, 227, 216, 162, 143, 138, 118, 117, 102, 101, 66, 66, 65, 62, 53, 52,
        51, 49, 40, 39, 38, 35, 33, 32, 32, 31, 26, 25, 24, 22, 22, 21, 20, 20, 19, 19,
        19, 18, 17, 16, 16, 15, 15, 14, 14, 14, 14, 14, 13, 13, 12, 12, 12, 12, 12, 12,
        11, 11, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8,
        8, 8, 8, 8, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5,
    )
    private const val AFTER_LATIN_TOTAL = 5070.0
    private const val AFTER_LATIN_SMOOTHING = 500.0

    private fun transition(row: Int, col: Int): Double =
        decode(UNIT_TRANSITION_TABLE[row * UNIT_COLS + col], UNIT_LO, UNIT_HI)

    // 단위 전이 표의 행(직전 단위) / 열(다음 단위). 낱자모는 호환 자모 51자(ㄱ~ㅣ) 각각이 한 칸.
    private const val UNIT_JAMO_FIRST = 0x3131
    private const val UNIT_JAMO_LAST = 0x3163
    private const val ROW_BOS = 0
    private const val ROW_SYLLABLE = 1
    private const val ROW_JAMO = 2
    private const val COL_SYLLABLE = 0
    private const val COL_EOS = 1
    private const val COL_JAMO = 2
    private const val UNIT_COLS = 53
    private const val UNIT_LO = -18.0
    private const val UNIT_HI = 0.0

    /**
     * 구어체 단어 안에서 "직전 단위 → 다음 단위" 로그확률(53×53, [LEVELS]단계 양자화). 단위는
     * 음절(종류 무관 1칸) / 낱자모 51종 / 단어 시작·끝. 음절이 무엇인지는 [KO_SYLLABLE_TABLE] 이
     * 따로 곱해진다.
     *
     * 네이버 영화리뷰 NSMC **train** 분할(15만 문장)에서 세고, 행마다 전체 분포 쪽으로 평활
     * (α=20)했다 — 평가는 겹치지 않는 test 분할로 했다. 실측이 보여주는 것:
     * - 음절 뒤 낱모음 `ㅕ` 는 -12.7(`cpu`→`체ㅕ`), 반면 음절 뒤 `ㅋ` -6.9 · `ㅠ` -7.8
     * - 같은 낱자모 반복은 거의 확실(`ㅋ→ㅋ` -0.4 · `ㅠ→ㅠ` -0.7 · `ㄷ→ㄷ` -0.6)
     * - 초성 약어 짝도 흔함(`ㅈ→ㄴ` -1.4 · `ㅁ→ㅊ` -2.2), 반면 `ㅊ→ㄴ` 은 -11.4(`css`)
     * 낱자모 하나하나의 빈도만 보면(unigram) `ㅕ` 도 -12.1 로 드물지만 "어디에 붙었는가"를 몰라
     * `ㅜㅜ`/`ㅎㅎㅎ`/`ㅁㅊ` 같은 흔한 초성 표현을 영어 약어와 가르지 못했다.
     */
    private const val UNIT_TRANSITION_TABLE =
        "~?PHAL;!U>K7!!!!!!ON=LTKTPVFe=D_HGC!E@B!I7!!=Z!!!`^7J|xF=!G!1J5B!!!!!!!CA9;F;LDH;[89SHA<!E@>!B7!!AR7!1VS1Fwzuc" +
        ",i%`g+g%%!!!!!`>`6j`hcDio42`;j0!8`0!9)!!1I'%!Ne'9yvjv3D,.L2C,,####*BE4=H=jsKgo;9SB=g*?g7*@0##8P.,*Ug.g{ymDpJ24" +
        "R8I22****0HK:CNCQJRDcA?YHC>0E@=0F6**>V420[X4Fyxg8.t&(i,g&&!!!!#ia/7h7ohFabaaMge2#e41#:*!!2J(&#Oa(:|xME;L46T9K4" +
        "4,,,,1JM<EOERKSFeC@[IE?1FB?1H8,,@W641^Z6H|wME;L45S9J44,,,,1IM<EnEnKSFeC@[IE?1FA?1G8,,@W541^Z5Hqyb1&a!!{Y]!!!!!" +
        "!!5d'0d0dY>1e.,]_]*!2Y*!3!!!+C!!!HE!3|wLD:K35S8I33++++0IL;DNDQJREdB?ZHD>0E@>0n7++?V530]Y5Gwwr9/i(*G-t((!!!!%bA" +
        "09C9ubGlf74fb93%:b3%;,!!4K*(%QN*b|wME;L46T9K44,,,,1JM<EOERKSFeC@[IE?1FB?1H8,,@W641^Z6H|wME;o46T9K44,,,,1JM<EOE" +
        "RKSFeC@[IE?1FB?1H8,,@W641^Z6H}wNF<M46T:K44,,,,2JM<EPESLTFeCA[JE@2GB?2H8,,@X642^Z6H}wNF<M46T:K44,,,,2JM<EPESLTF" +
        "eCA[JE@2GB?2H8,,@X642^Z6H}wNF<M46T:K44,,,,2JM<EPESLTFeCA[JE@2GB?2H8,,@X642^Z6H}wNF<M46T:K44,,,,2JM<EPESLTFeCA[" +
        "JE@2GB?2H8,,@X642^Z6H|wNF;L46T:K44,,,,2JM<EPESKSFeCA[IE?2GB?2H8,,@X642^Z6HwzB:0o)*H.g))!!!!&nB1:g:noHrd75c>:4&" +
        ";63&<-!!4L*)&Rc*<u{`7-`&'n+g&&!!!!``h`6s6kfE8d42L;71!830!9)!!1I'&!dd'`{wKD9J24R8I22****0HK:CmCQIQDmm>YpC=0E@=0" +
        "F6**>V420[X4F|xF>4E-.g2C--%%%%*BF5>H>KDL?^<9TB>8*?:8*@1%%9P.-*VS.Auvnj^a!%f)c!!!!!!!ey+4p^maB5hhag84.!^1.!7'!!" +
        "/G%^!eh%7yuF>4g-.g2w--%%%%*BFk>noKDL?^<gkm>8*?:8*@1%%9P.g*VS.guyb2'f!Ze&f!^!!!!!cg(1k1x`?2kZ-df1+Zb^+!4#!!,D!!" +
        "!`b!fysl9.w')b-r''!!!!%le/bmbepF9W64nbg2%:52%b+!!3K)'%PM)bnzY1'8!!?%6!!!!!!!59(1Y1>Y{2O.,Gb1+!Y-*!3#!!+C!!!IE!" +
        "Yyyf=3D,.K1r,,####)BE4=G=JjKsq;8gA=7)>97)?0##8O.,)UR.@pw[T!I!!W!L!!!!!!!I(!!P!RIIN|WIY#!L!!!!!!!!!!I!!!NI!!xwH" +
        "@6i.0N4E..&&&&,DG6?J?iFMutt;UD?:,A<9,B2&&:R0.,WT0BzxIA7H02k5k00((((-FI8AKANkOBn?qnEA;-B>;kD4((<S20-YV2DpzU)!Q!" +
        "!i!Z!!!!!!!X1!)[)[U7*bW#zU)!!*%!!+!!!QR!!!AW!,zxg;0A)+l/d))!!!!'dd1:gdodHdk85kq:4'g74'=-!!5M+)'dd+={xk>4k-.L2C" +
        "--%%%%*BF5>H>KgL?];9TBs8*?:7*@1%%8P.-*VR.gyykB8I12P6G11)))).FJ9BoBOkPCo?=XkBk.C>;.D5))<T21.Zk2D}vNF;L46T:K44,," +
        ",,2JM<EPESKSFeCA[IE?2GB?2H8,,@X642^Z6Hzwf=3f,-K1j,,!!!!)fE4<GfpfK>g:ffj=7)s96)ff!!7j-,)fQ-f{wjA6G/1O5Fj/''''-E" +
        "H7@K@jjNA`><mDj:-Bt:-C3'';S1/-XU1C{xJB8o13P6k11)))).GJ9BLBkHPCb@=lkB<.k><.D5))=T31.ZW3E}vNF;L46T:K44,,,,2JM<EP" +
        "ESKSFeCA[IE?2GB?2H8,,@X642^Z6HwyD<2C+,J0A++!!!!(@D3;e;IBJ=f97k@<6(e85(y/!!6N,+(TP,>}wME;K35S9J33++++1IL;DODRKR" +
        "EdB@ZID?1FA>1G7++?W531]Y5G}wNF<M46T:K44,,,,2JM<EPESLTFeCA[JE@2GB?2H8,,@X642^Z6H}wNF<M46T:K44,,,,2JM<EPESLTFeCA" +
        "[JE@2GB?2H8,,@X642^Z6HzxJB7k02P6k00((((.Fk8ALApHOBa?=WnA;.k>;.D4((kn20.YV2Drz5-!3!!;!U!!!!!!!14!,U,:3:-_U(]1U&" +
        "!.)&!/!!!'y!!!me!/}wME;L45S9J44,,,,1IM<EOERKSFeC@[IE?1FA?1G8,,@W541^Z5H|vME;L46T9K44,,,,1JM<ErERKSFeC@[IE?1FB?" +
        "1H8,,@W641^Z6H}vNF;L46T:K44,,,,2JM<EPESKSFeCA[IE?2GB?2H8,,@X642^Z6HqzO'!.!!5!O!!!!!!!,O!'X'X-5([%SP+'!!(!O!O!!" +
        "!!i!!!z`!*rzX*!X!!S!0!!!!!!!VS!*`*^S8+e(%VS*#!+'#![!!!%e!!!byS-|yME;L45S9J44,,,,1IM<EOERKSFeC@[IE?1FA?1G8,,@W5" +
        "41^Z5H{wh<1h*,h0h**!!!!(hC2;eemBI<f97Qj;6(e85(>.!!6N,*(SP,p"

    /**
     * 영어 가설을 "일반 단어 + 약어" 로 넓힌 로그확률. trigram([englishLogProb])은 소설·뉴스로
     * 학습해 `cpu`/`gpu`/`ssd` 같은 약어를 흔치 않은 글자 배열이라며 매우 낮게 본다(`cpu` ≈ -27).
     * 그러면 이미 한영타로 판정된 선택 안에서 `cpu` 가 `체ㅕ` 에게 진다 — 사용자가 실제로 겪은
     * 버그. 약어는 사실상 "아무 글자나 짧게 늘어놓은 것"이라 글자당 균등확률(1/26)로 보는 별도
     * 부류를 [ACRONYM_LOG_PRIOR] 의 사전확률로 섞는다.
     *
     * ⚠️ 약어 부류에는 반드시 **길이 분포**([ACRONYM_LENGTH_LOG])가 있어야 한다. 균등 글자 모델은
     * 글자당 -3.26 인데 한국어 음절 모델은 글자당 약 -4.9 라(`너무너무` = -39.2 / 8글자), 길이
     * 제한 없이 섞으면 긴 진짜 한영타(`sjansjan`)까지 "약어"로 설명돼 교체에서 빠졌다. 약어는
     * 실제로 2~4글자가 대부분이고 7글자 이상은 사실상 없다.
     */
    fun englishOrAcronymLogProb(latin: String): Double {
        val word = englishLogProb(latin)
        val lengthLog = ACRONYM_LENGTH_LOG.getOrNull(latin.length) ?: return word
        val acronym = ACRONYM_LOG_PRIOR + lengthLog + latin.length * LOG_UNIFORM_LETTER
        val hi = maxOf(word, acronym)
        return hi + Math.log(Math.exp(word - hi) + Math.exp(acronym - hi))
    }

    /** 약어 부류의 사전 로그확률(≈5%) — NSMC 평가로 정함([HangulConverter] 문맥 변환 문서 참조). */
    private const val ACRONYM_LOG_PRIOR = -3.0
    private val LOG_UNIFORM_LETTER = Math.log(1.0 / 26)

    /** 약어 길이 분포(인덱스 = 글자 수, 1~6). `B`급 / `ai` / `cpu` / `html` / `https` / `ssdnvme`… */
    private val ACRONYM_LENGTH_LOG: DoubleArray =
        doubleArrayOf(0.0, 0.10, 0.25, 0.35, 0.20, 0.07, 0.03).map { if (it == 0.0) Double.NEGATIVE_INFINITY else Math.log(it) }
            .toDoubleArray()

    /** 소문자 라틴 글자열이 영어 단어로 나올 로그확률(합). 앞 경계 2개·뒤 경계 1개를 붙인 trigram. */
    fun englishLogProb(latin: String): Double {
        var total = 0.0
        var s1 = 26
        var s2 = 26
        for (i in 0..latin.length) {
            val s3 = if (i < latin.length) symbolIndex(latin[i].lowercaseChar()) else 26
            total += decode(EN_TRIGRAM_TABLE[s1 * 729 + s2 * 27 + s3], EN_LO, EN_HI)
            s1 = s2
            s2 = s3
        }
        return total
    }

    /**
     * 글자당 점수 → 로짓의 척도([wordLogit]). 예전엔 점수 [TYPO_THRESHOLD](3.0)가 정확히 기본
     * 임계값 70% 였고, 지금은 여기에 [CALIBRATION_SHIFT] 를 더 민 값이 최종 신뢰도다 — 사용자가
     * 임계값을 올리면 더 보수적(오탐↓·미탐↑), 내리면 더 적극적으로 동작하는 관계는 그대로다.
     */
    private const val SCALE = 2.0
    private const val CENTER = TYPO_THRESHOLD - 0.8472978603872034 * SCALE // logit(0.70)
}
