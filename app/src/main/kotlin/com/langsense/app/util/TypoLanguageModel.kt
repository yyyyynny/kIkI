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
    private const val KO_LO = -23.010597421482757
    private const val KO_HI = -5.130075222900358

    /** 조합 실패해 낱자모로 남은 글자의 로그확률(실제 한국어 단어엔 없으므로 최저값). */
    private const val KO_FLOOR = -23.010597421482757

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
        "xg!!k!!F^/!!!!!!hWRBYjX!!dD+nZ!!+!!!Z!!!!!!!/+!D/J!!!!!!8+!!!!!!8!!!!!!!!!!!!/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!kK+!h!!K^!!!!!!!aJ!n!+!!!D!!p!!!R!!!K!!!!!!!4!!8]!!!!!!!_hO!b!!!j!!!!!!!QJ!!Uo!!!C!!m!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!x`!!V!!T`!/!!!!1HI!^!p;!!!!!rJ!!m!!!L!!!!!!!@!!!!c!!!!!!8!!!E!!!!!!!!!!!!!!!!3!!!!!!Y!!!!!!!/!!!!!!!!!!!!F" +
        "!!!!!!m!!!!!!!!!!!!!!!!!!+!!!!!!!!ps!!j!!LV<@!!!!!+F!D!X8!!!!!>+!!i!!!J!!!!!!!!!!!4!!!!!!!R!!!!!!!/!!!!!!!!!!!" +
        "!!!!!!!!_!!!?!!!+!!!!!!!/!!/!!!!!!!!c!!!Z!!!:!!!!!!!!!!!!!!!!!!!rb!!d!!!b9!!!!!!id!G!N!!!/!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!w7!![!!!_!!!!!!!g>!K!:!!!!R!h6A!A!!!K!+!!!!!IH!!!>+!!I!!S!!!<!!!7!!!!!!!!!!!9+!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!N7T!!!!!4!!!!!!!1C!E+?!!!!!!a!!!+!!!!!!!!!!!!!!!!!!!!!!!G!!!!!!!!!!!!!" +
        "!!!!!!H!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!OJ!!1!!!J!!!!!!!EL!6!FBS!!!!4=!!!!!!!!!!!!!!!!!!!=!!!!!!A!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!G!!!/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!V/!!O!!!H!!!!!!<R/!3!3/!!!!!G!!!!!!!!!" +
        "!!!!!!!!!!A/!!!!!!?!!!+!!!!!!!!!!!!!!!!!!!!!!!O!!!E!!!<!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!Q!!!N!N!" +
        "X!+!!!!DM!!C!/!!!]!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!X:!!H!!!C!!!!!!!J1!!!6!!!!!!sVB!e!!!cB!!!!!!iT!D]OWA!@!Ol8!!W!" +
        "!!O!!!!!!!CF!6YO!!!!!!Y!!!+!!!/!!!!!!!4!!!!S!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!`C!6J!!!Z!+Q!!!!^3!=4+!!!!!RdR!!" +
        "E!!!K!!!!!!!;<!Y3+!!!!!!_I!!s!!!!!!!!!!!Z!!!IG!!:!!!:!!!!!!!!!!!!!!!!!!!!!!!!!!!iT!!a!!!S!!!!!!!E:!!!_!!!!]YA!" +
        "!!!!!!!!!!!!!!!!!!?!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!T!!!!!!!!!!!!!!!!!!!!!!!!!!!B!!!!!!!/!!!!!!!!!!!!?!!!!!!" +
        "_7!!Z!!!F!!!!!!!C=!!!7!!!!!!F!!!!!!!!!!!!!!!!!!!:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!K!!!A!!!1!!!!!!!!!!!!!!!!!" +
        "!!^!!!!!!!!!!!!!!!S!!!!4!!!!!!]W!!{!!!^A!!!!!!=!!!!dP!!!?!E!!!!!!!!!!!!!!!!!!!!!!!!!!!kR!!Y!!!L!!!!!!!W<!K!V!!" +
        "!!+!}V?!l!!LgEG!!!!3cW!M!m!4!!!Ew:!!N!!!@!!!!!!!>1!L8=!!!!!!1!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!gX!!i!!!S!!B!!!!L@!M!K!;!!M!k=!!U!!!W!!!!!!!>!!3!=!!!!!!;!!!+!!!!!!!!!!!!!!!4!!!!!!!+!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!uh!!V!!Ld!!!!!!!EL!8!p!3!!!!!!!!/!!!!!!!!!!!!!!!!!!!!!!!W!!!!!!!!!!!!!!!!!!+Z!!!!!!!p!!!k!!!]!!!!!!!L:" +
        "!!!3!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!iI!!V!!!V!!!!!!!B>!!!S!!!!!!C!!!!!!!!!!!!!!!!!!!G!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!_!!!!!!!+!!!!!!!!!!N!/!!!!!!Q!!!!!!!@!!!!!!!9!!!!!!!!!!!o^!!b!!Qr!9!!!!!RM!X!l!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!fF!!M!!AL!!!!!!!EA!<1RC!!!1!dH!!E!!!R!!!!!!!C!!+;R!!!!!3i!!!D!!!8!!!!!!!!!!!4<!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!YD!!U!!!U!!!!!!!+!!8=!!!!!!QK!!!:!!!4!!!!!!!!!!!/!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!fL!!!!!!9!!!!!!!!!!+!A!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!JJ!!+!!!3!!!!!!H!!!!!@!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!U!!!D!!!@!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!R:!!" +
        "D!!CJ!!!!!!!?+!V!!!!!!!!J!!!8!!!!!!!!!!!4!!!!!!!!!!!H!!!@!!!!!!!!!!!!!!!!+!!!!!!s_!!f!!!M!!!!!!!cO!3Wb!!!!+8gW" +
        "!!`!!!;!!!!!!!XN!PDS!!!!!!EX!!+!!!!!!!!!!!!!!!!`!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!mV!!a!!!O!!!!!!!a_!GUJ!!!!!W" +
        "jV!!V!!!H!!!!!!!H>!G!3!!!!!!ii!!b!!!R!!!!!!!KS!G^g!!!!!!]!!!3!!!!!!!!!!!!!!!!!!!!!!!yg!!e!!!T!!!!!!!MR!Z!R!!!!" +
        "!!!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!S!!!9!!!/!!!!!!!/!!!!!!!!!!!b!!!!!!!!!!!!!!!!!!!!Q!!" +
        "!!!!gN!!P!!!I!!!!!!!KW!:!E!!!!!!N!!!!!!!!!!!!!!!!!!!E!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!J3!!+!!!/!!!!!!!8!!!!!" +
        "!!!!!!cV!!J!!!X!!!!!!!N!!!!T!!!!!!l=!!e!!!u!!!!!!!b>!C!N!!!!I!!!!!!!!!!!!!!!!!!!!!!!!!!!!!uZ!!e!!!X!!!!!!!ac!P" +
        "!Y!!!!!!nc!!p!c;gJ!!!!!!C?!N!c_!!Z!/hW!!Y!!!8!!!!!!!=I!<1ZQ+!!!!/!!!/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!`Y!!]!!!R!!!!!!!L!!H!K/!!!!+dQ!!S!!!N!!!!!!!S!!B!@!!!!!!m/!!m!!!T!!!!!!!!!!!;n!X!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!ld!CV!!!Z!!!!!!!V@!b!S!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!4!!!+!!!/!!!!!" +
        "!!!!!+!/!!!!!!W!!!!!!!/!!!!!!!!!!!!!!!!!!!mOJ!n!!Oh!+!!!!!D!!C!D!!!+!!E!!!@!!!@!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!@!!!C!!!<!!!!!!!!!!!!!!!!!!!Y!!!+!!!H!!!!!!!4!!!!!!!!!!!V!!!+!!!8!!!!!!!1!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!nN!!l!!S^!!!!!!!68!@:M!^!N!!jfW!k!!gm^!F!!!!UJ!6!l+!!G!!ha!!S!!!G!!!!!!!K!!B!P!!!;!!4!!!3!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!gE!!f!!!`!!!!!!!bh!Q!@D!!!!!e@!!Y!!!X!!!!!!!+!!?!B!!!!!!KV!!d!" +
        "!!b!!!!!!!!D!+1d!!!B!!+!!!/!!!!!!!!!!!!!!!!!!!!!!!qe7!h!!!]!!!!!!!S>!O!]!!!!!!L!!!!!!!!!!!!!!!!!!+K!!!!!!!+!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!:!!!+!!!6!!!!!!!!4!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!sg!!k!!/hK!!!!!!F!!D!S!!!W!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!X!!!!!!!+!!!!!!!!!!!!!!!!!!!T!!!6!!!;!!!!!!!1!!!!!!!!!!!" +
        "e+!!J!!!`!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!oR!!Y!!!Z!!!!!!!=+!J!RKR!!!!Y9!!1!!!S!!!!!!!!!!!9B!!!!" +
        "!/O;!!@!!!1!!!!!!!/!!>46!!!!!!!!!!!!!!!!!!!!!!8!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!B7!!F!!C8!!!!!!!!!!84/!!" +
        "!!!!4!!!!!!!!!!!!!!!!!!!!!!!!!!!K!!!!!!!!!!!!!!!+!!4!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!<!!!!!!!!!!!!!!!7O!!!9" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!9!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!L!!!X!!!E!!!!!!!@!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!G!!!K!!!1!!!!!!!G+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!B1!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!vQ!!j!!!b!Q!!!!!bI!>Er!!!1!!d_!!O!!!B!!!!!!!M!!9+k!!!!!!S3!!?!!!A!!!!!!!;>!E!C!!!!+!C!!!!!!!!!!!!!" +
        "!!!!!!!1!!!!!!weH!p!!4h+!!!!!!VS!PPq!!!!!!nI!!]!!!U!!!!!!!HK!O13!!!!!!Q4!!]!!!M!!!!!!!/!!=L3!!!!!!N!!!;!!!E!!!" +
        "!!!!!1!!!/!!!!!!oi+!a!!!V!!!!!!!B/!D!b!!!=!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!S!!!!!!!!!!!!!!!!!!!!!!!!!!!M!!!4!!!!!" +
        "!!!!!!!!!+!!!!!!!!Z!!!@!!!4!!!!!!!+G!1!!!!!!!!tZ!!b!!/c!!!!!!!T!!K!N!9!=J!1!!!!!!!!!!!!!!!!!!!/!!!!!!!@!!!+!!!" +
        ">!!!!!!!!!!!!!!!!!!!S1!!?!!!A!!!!!!!<S!+!!!!!!!!^!!!1!!!G!!!!!!!A!!G!8!!!!!!u+!!Z!!![8!!!!!!L`!R!i!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!vi!!o!!>k!!!!!!HgW!;![!!!!T!XE!!K!!!J!!!!!!!8!!!6P!!!!!O/!!!/!!!!!!!!!!!+!!!!9!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!ZF!!<!!!A!!!!!!!>8!!P1!!!!!!>!!!4!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!K;!!C!!KD!!!!!!!!!!!!!!!!!!!:!!!!!!!!!!!!!!!!!!!3!!!!!!!" +
        "A!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!>D!!?!!!+!!!!!!!!!!!!3!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!^/!!Q!!!O!!!!!!!K4!!!!!!!!!!B!!!!!!!!!!!!!!!!!!!!!!!!!!!]Q!!N!!+!!!!!!!!+<!@!J!!!!!!tc!!lMg!d!!!!!!DZ^+Jg[" +
        "!!!!_!aX!!U!!![!!!!!!!BO!;6J!!!!!!jg!!M!!!D!!A!3!!3/!8!h!!!=!:L!!!!!!!!!!!!!!!!!!!!!!!!!!!sc!!f@!Y[@!!!!!!ZigP" +
        "qH!!6!@!|N!!`!!!Z!!!!!!!O<!G!?!!!!!!pk@!o!!!e!!/!!!!ZR+Enn!!!;L+h!!!A!!!F!!!!!!!!3!O+!!!!!!!mX!!b!!!f!R+!!!FCG" +
        "!O!V!/!!!!o=!!`!!!C!!!!!!!46!C]b!!!!!!T+!!4!!!!!!!!!!!!!!!!+!!!!!!f!!!I!!!/!!!!!!!!!!!!!!!!!!!jY!!I!!!+!!!!!!!" +
        "41!!!k!!!!!!nV!!j!!!d!!!!!!!^6!U!R!!!!!!bA!!q!!!n!!!!!!!;!!!U:!!!!!![1!!G!!!H!!!!!!!;P!+!+!!!!!!q=!!S!!!P!!!!!" +
        "!!D+!G!D!!!!!!na!!]!!!_!!!!!!!!!!+!Z!!!!!!u=!!v!!!y!!!!!6!jK!+!_!!!!!+{!!!+!!!!!!!!!!!!!!!!+!!!!!!~^!!u!!!tP!!" +
        "!!!Rih!RqWI!+!G!tk!!U!A![!!!!!!!X^!?1rE!!!!!lN!!:!!!+!!!!!!!<+!:+b!!!!!!9!!!+!+!!!!!!!!!+!!!!8!!!!!!+!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!eq!!t!!!c!O/!!!!h`!<!tC!!!!!rP!!O!!!Q!!!!!!!1+!8!+!!!!!!`/!!1!!!!!!!!!!!!!!!a!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!oc!!b!!!R!!!!!!!MJ!!!l+8!!!ZY+!!!!!!!!!!!!!!!!!!!/!!!!!!/!!!!!!!!!!!!!!!!!!!!!!!!!!!Y!!!6!" +
        "!!!!!!!!!!!!!+!!!!!!!!M!!!1!!!!!!!!!!!!!!!!!!!!!!!r]!!d!!!]!!!!!!!B9!!!o!!!!!!K!!!!!!!!!!!!!!!!!!!M!!!!!!!+!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!M!!!>!!!=!!!!!!!!!!!!!!!!!!!D!!!+!!!4!!!!!!!!!!!!!!!!!!!cY!!_!!!S!!!!!!!Q4!!!e!!!!!!+!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!xh!!n!!!d!;!!!!!Re!S!^D!!C=!TP!!8!4!;!!O!!!!43!!+A!!!!!!a!!!!!!!!!!!!!!!!!!+?!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!+!!!!!!!GC!!6!!!B!!!!!!!!!!!!;!!!!!!1!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!A^!!+!!!!!!!!!!!!!!!!1!K!!!!!1!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!7@!!+!!!1!!!!!!!+!!!!/" +
        "!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!>!!!!!!!!!!!!!!!I!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!QR!!3!!!D!!!!!!!E/!!!/D!!!!+k^!!]!G!a!!!!!!!c/" +
        "!4>b[!!!!!_c!!E!!!C!!!!!!!S1!>8G!!!!!!6!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!fW!!g!!!b!!!!!!!" +
        "MO!b!g+!!!!!k!!!D!!!K!!!!!!!!!!1!!!!!!!!]!!!+!!!!!!!!!!!!!!!W+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!fX!!R!!!=!!!!!" +
        "!!;+!J!e!!!!!!!!!!>!!!T!!!!!!!!!!!!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!j!!!!!!!!!!!!!!!!!!!!!!!!!!!3!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!gd!!U!!!k!!!!!!!Q=!;!`!!!!!!N!!!!!!!!!!!!!!!!!!!E!!!!!!!8!!!!!!!!!!!!!!!!!!!!!!!!!!!`!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!@!!!!!!!!!!!!!!!!!!!!!!!!!!!^`!!/!!!8!!!!!!!?!!/!Z!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!mV!!a!!!" +
        "U/!!!!!!^E!D!Y!!!!!!i=!!S!!!W!!!!!!!J?!8!B!!!!!![1!!L!!!O!!!!!!!UD!@!3!!!!!!4!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!_8!!U!!=V!!!!!!!X]!OG?!!!!!!`+!!I!!!R!!!!!!!=+!V!!!!!!!!W!!!1!!!+!!!!!!!++!!X!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!fC!!Z!!!V!!!!!!!RA!H!W!!!!!!G!!!8!!!4!!!!!!!!!!!!@!!!!!!O!!!!!!!!!!!!!!!!!!!!+!!!!!!:!" +
        "!!!!!!>!!!!!!!!!!!!!!!!!!!Q!!!!!!!!!!!!!!!!!!!!!!!!!!!_<!!A!!!N!!!!!!!E1!!!>!!!!!!I!!!:!!!C!!!!!!!!!!!!!!!!!!!" +
        "G!!!!!!!!!!!!!!!!!!!!!!!!!!!L7!!H!!!7!!!!!!!!!!+!!!!!!!!R!!!+!!!8!!!!!!!/!!!!!!!!!!!i!!!^!!!`!!!!!!!S/!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!gH!!V!!!T!!!!!!!H<!C!U!!!!!!lV!!a!!!b1!!!!!!ST!JBS!!!!!!g^!!F!!!@!!!!!!!GC!/6L!!" +
        "!!!!/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!lJ!!]!!!V!!!!!!!C8!B48!!!!!!cQ!!S!!!Y!!!!!!!U=!1!6" +
        "!!!!!!7!!!!!!!!!!!!!!!!!!!6!!!!!!!!!!!E!!!!!!!!!!!!!!!!!!!!!!!gN!!U!!!S!!!!!!!IN!+!m!!!!/!!!!!!!!!!!!!!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!^!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!g=!!M!!!F!!!!!!!H:" +
        "!!!@!!!!!!1!!!!!!!!!!!!!!!!!!!+!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!M!!!+!!!+!!!!!!!!!!!!6!!!!!!T!!!A!!!3!!!!!!!" +
        ">!!!!!!!!!!!pe!!R!!<^!!!!!!!H+!+!!!!!!!!4!!!!!!!!!!!!!!!!!!!!!!!!!!!eP!!V!!!M!!!!!!!a=!!!V!!!!!!jEC!h!!!Y!!!!!" +
        "!!?O!A4E!!!3!!dM!!V!!!A!!!!!!!B/!@!N!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!_/!!P!!!I!!!" +
        "!!!!C6!!+I!!!!!!bH!!T!!!N!!!!!!!/>!A!=!!!!!!K!!!d!!!S!!!!!!!>!!!>g!!!!!![!!!!!!!!!!!!!!!!!!!!!!!!!!!jb!!^!!![!" +
        "!!!!!!R!!1!D!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!4!!!+!!!/!!!!!!!!!!!!!!!!!!!j!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!VB!!K!!!W!!!!!!!a!!D!]!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!E!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!P!!!!!!!B!!!!!!!!!!!!!!!!!!!l!!!Q!!!c!!!!!!!G!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!h_!!" +
        "U!!!a!!!!!!!8?!B!V!!!!!!zm!!x!!!j!!!!>!!gk!L!d!!!!!!s_!!G!!!:!!!!!!!L/!Dqk!!!!!!4!!!!!!!!!!!!+!!!!!!!d!!!!!!!!" +
        "!!!!!!!!!!!!!!!!!!!!!!!!!!^;!!]!!!F!!!!!!!`/!A!K!!!!!!ZA!!Q!!!T!!!!!!!8+!1!+!!!!!!Z_!!i!!!S!!!!!!!Vd!!Yf!!!!!!" +
        "Z!!!!!!!!!!!!!!!!!!!!!!!!!!!jZ!!^!!!V!!!!!!!ZE!A!_!!!+!!od!!d!!!e!!!!!!!!!!/!e!!!!!!++!!!!!!!!!!!!!!!!!=!!!!!!" +
        "!!o^!!!!!!+!!!!!!!!!!D!L!!!!!!]!!!!!!!!!!!!!!!!!!!!!!!!!!!m3!![!!!P!!!!/!!F!!L!:!!!!!!/!!!@!!!N!!!!!!!!!!!!!!!" +
        "!!!!N!!!!!!!!!!!!!!!!!!!!+!!!!!!Z!!!1!!!E!!!!!!!/I!!!!!!!!!![!!!!!!!8!!!!!!!/!!!!J!!!!!!VT!!T!!!RD!!!!!!ER!!![" +
        "!!!G!!a+!!K!!!!!!!!!!!!!!!!!!!!!!!h;!!T!!!S!!!!!!!YK!+!8!!!!!!"

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
     * 한영타 점수 — 클수록 "한글을 영문 자판에서 친 것"에 가깝다. [latin] 은 선택된 원문 토큰,
     * [converted] 는 그것을 [HangulConverter.convertEngToKor] 로 변환한 결과.
     * 변환 결과에 한글이 전혀 없으면(판단 근거 없음) null.
     */
    fun score(latin: String, converted: String): Double? {
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

        return (koTotal - enTotal) / latin.length.coerceAtLeast(1)
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
