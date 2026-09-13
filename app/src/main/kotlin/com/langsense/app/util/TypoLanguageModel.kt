package com.langsense.app.util

/**
 * 한영타 판정용 통계 언어 모델 — "이 라틴 문자열은 실제 영어인가, 아니면 한글을 영문 자판에서
 * 친 것인가"를 **두 가설의 우도비(likelihood ratio)** 로 판정한다(2026-09 도입).
 *
 * ## 왜 우도비인가
 * 이전 방식은 "영어스러움"(영어 문자 bigram 점수)만 봤다. 그러면 `work`(→재가)처럼 영어로도
 * 흔하고 한글로도 그럴듯하게 조합되는 단어에서 판단 근거가 한쪽뿐이라 흔들린다. 실제로 대량
 * 검증에서 `work` 가 진짜 한영타인 `dkssud`(→안녕)보다 "더 한영타 같다"고 나오는 역전까지
 * 있었다. 두 가설을 **함께** 보면 이 역전이 사라진다:
 *
 *   점수 = [log P(변환결과 | 한국어 모델) − log P(원문 | 영어 모델)] / 라틴 글자 수
 *
 * 두 로그확률을 **같은 분모**(라틴 글자 수)로 나누는 게 핵심이다 — 한쪽은 음절당, 다른 쪽은
 * 글자당으로 정규화하면 한글 음절 수와 라틴 글자 수의 비(보통 1:2~3)가 섞여 위 역전이 생긴다.
 *
 * ## 두 모델
 * - **한국어 음절 unigram**([KO_SYLLABLE_TABLE]): 완성형 11,172자 각각의 로그확률. 실제
 *   한국어에서 쓰이는 음절은 그중 일부(실측 약 2천여 개)뿐이라, 영어 단어를 두벌식으로 변환했을
 *   때 나오는 `뮴`/`쳔`/`퍙` 같은 음절은 확률이 바닥이다 — 이것이 가장 강한 신호다. 조합에
 *   실패해 낱자모로 남은 것은 실제 한국어 단어에 없으므로 [KO_FLOOR](최저 확률)로 처리한다.
 * - **영어 문자 trigram**([EN_TRIGRAM_TABLE]): 소문자 26자 + 단어 경계(`^`) 27심볼의 3연속
 *   조합 로그확률(27³=19,683칸). 단어 경계를 심볼로 넣어 "영어 단어가 그렇게 시작/끝나는가"까지
 *   본다.
 *
 * ## 학습 데이터(2026-09)
 * 단어 목록이 아니라 **실제 글**에서 빈도 가중으로 학습했다 — 단어 목록은 흔한 단어와 사어를
 * 똑같이 1회로 세어 실제 분포를 왜곡한다.
 * - 영어: Project Gutenberg 고전 소설·에세이 29권 + AG News 뉴스 기사(총 4,890만 자,
 *   고유 단어 93,992개, 연 출현 834만 회)
 * - 한국어: KorQuAD(한국어 위키 본문) + KLUE-YNAT(뉴스 제목) + 한국어 위키백과 전체 덤프
 *
 * ## 성능(학습에 전혀 쓰지 않은 데이터로 측정)
 * 영어 사전 37만 단어 중 학습에 없던 34만 단어를 오탐 평가에, 실제 한국어 말뭉치에서 뽑은
 * 토큰을 [HangulConverter.convertKorToEng] 로 되돌린 "진짜 한영타" 14.7만 건을 감지 평가에 썼다.
 * 기본 임계값([TYPO_THRESHOLD]=3.0, 라틴 3글자 이상)에서 **오탐 0.03%, 감지율 98.2%**.
 * 직전 방식(스톱워드 + 영어 bigram)은 같은 사전 기준 오탐 0.96% 였다.
 *
 * ## 테이블 표현
 * 두 표 모두 로그확률을 [LEVELS](91)단계로 선형 양자화해 인쇄 가능 ASCII 문자 하나에 대응시킨
 * 문자열이다(Kotlin 리터럴에서 이스케이프가 필요한 `"` `\` `$` 는 제외). 합쳐 약 30KB로,
 * 외부 파일·라이브러리 없이 순수 Kotlin 상수만 쓴다(최소 의존성 원칙). 조회는 배열 인덱싱
 * 한 번이라 저사양 기기에서도 토큰당 O(글자 수)로 끝난다.
 */
internal object TypoLanguageModel {

    /** 기본 판정 임계값(점수 ≥ 이 값이면 한영타로 본다) — 위 "성능" 항목의 보정 결과. */
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

    /**
     * 첫 글자를 제외한 위치의 대문자에 주는 가산점. 두벌식에서 쌍자음(ㄲㄸㅃㅆㅉ)·복합모음(ㅒㅖ)은
     * Shift 로 치므로 진짜 한영타에는 단어 중간 대문자가 흔하다(`했다`→`goTek`, `함께`→`gkaRp`).
     * 실측: 진짜 한영타의 12.1%가 이 형태인 반면 실제 영어 글에서는 0.26%(camelCase·약어 일부)
     * 뿐이라 약 50배 차이의 증거다. 영어 단어는 대부분 소문자라 이 가산점이 붙지 않으므로 오탐은
     * 늘지 않고(검증에서 변화 없음) 미탐만 줄어든다.
     */
    private const val INNER_UPPERCASE_BONUS = 2.0

    /**
     * 한영타 점수 — 클수록 "한글을 영문 자판에서 친 것"에 가깝다. [latin] 은 선택된 토큰의 글자만
     * 소문자로 모은 것, [converted] 는 원문 토큰을 [HangulConverter.convertEngToKor] 로 변환한
     * 결과, [innerUppercase] 는 첫 글자 외에 대문자가 있었는지([INNER_UPPERCASE_BONUS] 참조).
     * 변환 결과에 한글이 전혀 없으면(판단 근거 없음) null.
     */
    fun score(latin: String, converted: String, innerUppercase: Boolean = false): Double? {
        var koTotal = 0.0
        var units = 0
        for (ch in converted) {
            val code = ch.code
            when {
                code in HANGUL_FIRST..HANGUL_LAST -> {
                    koTotal += decode(KO_SYLLABLE_TABLE[code - HANGUL_FIRST], KO_LO, KO_HI)
                    units++
                }
                code in JAMO_FIRST..JAMO_LAST -> {
                    koTotal += KO_FLOOR
                    units++
                }
            }
        }
        if (units == 0) return null

        // 영어 trigram: 경계 심볼 2개를 앞에, 1개를 뒤에 붙여 시작/끝 패턴까지 반영
        var enTotal = 0.0
        var s1 = 26
        var s2 = 26
        for (i in 0..latin.length) {
            val s3 = if (i < latin.length) symbolIndex(latin[i].lowercaseChar()) else 26
            enTotal += decode(EN_TRIGRAM_TABLE[s1 * 729 + s2 * 27 + s3], EN_LO, EN_HI)
            s1 = s2
            s2 = s3
        }

        val base = (koTotal - enTotal) / latin.length.coerceAtLeast(1)
        return if (innerUppercase) base + INNER_UPPERCASE_BONUS else base
    }

    /**
     * 점수를 0~1 신뢰도로 변환(로지스틱) — 설정의 "신뢰도 임계값(%)"과 이어 붙이기 위한 것.
     * [TYPO_THRESHOLD](3.0)가 정확히 기본 임계값 70%에 대응하도록 [CENTER]/[SCALE] 을 맞췄다.
     * 사용자가 임계값을 올리면 더 보수적(오탐↓·미탐↑), 내리면 더 적극적으로 동작한다.
     */
    fun confidence(score: Double): Float {
        val z = (score - CENTER) / SCALE
        return (1.0 / (1.0 + Math.exp(-z))).toFloat()
    }

    private const val SCALE = 2.0
    private const val CENTER = TYPO_THRESHOLD - 0.8472978603872034 * SCALE // logit(0.70)
}
