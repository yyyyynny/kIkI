package com.langsense.app.util

/**
 * 한영타 판정 보조 표(2026-09, `TypoLanguageModel` 참조). 전부 실측 데이터에서 만든 뒤 91단계로 양자화했다 —
 * 만드는 방법·출처·검증 수치는 docs/한영타_검증.md. 손으로 고치지 말 것(tools/typo-model/build_tables.py 로 재생성).
 */
internal object TypoTables {
    /** 학습 글에 나온 한글 음절 3034자(부호점 순 — 이진 탐색). */
    const val POS_SYLLABLES =
        "가각간갅갇갈갉갊감갑값갓갔강갖갗갘같갚갛개객갠갤갬갭갯갰갱갵갸갹갼걀걈걋걍걑걔걘걜거걱걲건걷걸검겁겂것겄겅겆겇겈겉겊겋게겐겓겔겜겝겟겠겡겤겧겨격겪견겯결겸겹겻겼경겿곀곁곃계곅곌곗곘고곡곤곧골곪곬곯곰곱" +
        "곳공곶곷곸곺곻과곽관괄괌괍괏광괘괙괜괞괭괴괵괸괼굄굉교굔굣굥구국굮굯군굳굴굵굶굷굻굼굽굿궁궂궃궅궈궉권궐궛궜궤궨궬귀귁귄귈귐귑귓귘규귝균귤귬귯귱그극귻근귿글긁금급긋긍긐긑긔긛긬기긱긴긷길긺김깁깃깄깅깇" +
        "깈깉깊깋까깍깎깐깓깔깜깝깞깟깠깡깤깥깨깩깬깰깸깹깻깼깽꺄꺅꺆꺇꺍꺙꺜꺠꺤꺼꺽꺾껀껃껄껌껍껏껐껑껒껓께껜껫껬껭껰껳껴껸꼅꼇꼈꼐꼬꼭꼮꼰꼳꼴꼼꼽꼿꽁꽂꽃꽄꽅꽆꽈꽉꽌꽝꽤꽥꽦꽹꾀꾄꾐꾜꾱꾸꾹꾼꾿꿀꿇꿈꿉꿋꿍" +
        "꿎꿏꿐꿑꿓꿔꿘꿧꿨꿩꿬꿰꿴꿸뀌뀍뀐뀔뀜뀝뀨뀩뀰뀼끄끅끆끈끊끋끌끎끓끔끕끗끙끚끝끠끼끽끾낀낄낌낍낏낑낔나낙낚낛난낞낟날낡낢남납낫났낭낮낯낰낱낲낳내낵낸낻낼냄냅냇냈냉냊냌냐냑냔냘냠냣냤냥냨냬냼넀너넉넋넌널" +
        "넒넓넖넗넘넙넛넜넝넢넣네넥넨넫넬넴넵넷넸넹넼넿녀녁녂년녈념녓녔녕녘녜녠녤녱노녹논녿놀놂놃놈놉놋놌농놐놑높놓놔놕놘놜놧놨놬뇌뇔뇜뇨뇩뇬뇰뇸뇹뇽누눅눈눊눋눌눔눕눗눙눜눞눟눠눴눼뉘뉜뉠뉨뉩뉴뉵뉸뉼늄늇늉느늑" +
        "늒는늘늙늚늠늡늣능늦늨늪늬늰늴닁니닉닊닌닏닐님닙닛닝닟닠닢다닥닦단닫달닭닮닯닳담답닶닷닸당닺닻닼닿대댁댄댇댈댐댑댓댔댕댖댘댜댠댣댤댬댰댸더덕덖던덛덜덞덟덤덥덧덨덩덪덫덮덯데덱덴덷델뎀뎁뎃뎄뎅뎈뎋뎌뎍뎐" +
        "뎔뎜뎝뎟뎠뎡뎦뎨뎬뎰도독돈돋돌돎돐돓돔돕돗동돛돜돝돟돠돤돱돼됀됄됌됏됐됑되됙된될됨됩됫됬됭됴됸됻둏두둑둔둘둚둠둡둣둥둬둿뒀뒈뒌뒤뒥뒨뒬뒴뒷뒸뒹뒺듀듁듄듈듐듓듕드득든듣들듦듧듨듫듬듭듯등듴듵듷듸딍디딕딘" +
        "딛딜딤딥딧딨딩딪딫딬딮따딱딲딴딸땀땁땃땄땅땈땋때땍땐땓땔땜땝땟땠땡땨땬땸땽떄떈떌떔떘떙떠떡떢떤떨떫떰떱떳떴떵떻떼떽뗀뗄뗌뗍뗏뗐뗬뗴뗵뗸뗼또똑똔똗똘똠똣똥똬똭똴뙀뙁뙇뙤뙬뚜뚝뚠뚣뚤뚧뚫뚬뚯뚱뚸뛰뛴뛸뜀뜁" +
        "뜌뜨뜩뜬뜯뜰뜷뜸뜹뜻뜽띀띁띄띈띌띔띕띙띠띡띤띨띰띱띵라락란랃랄람랍랏랐랑랒랔랖랗래랙랜랟랠램랩랫랬랭랰랲랴략랸랼랽럄럅럇럈량럐럔럤러럭런럴럼럽럾럿렀렁렇레렉렌렐렘렙렛렜렝렠려력련렬렴렵렷렸령렿례롁롄롈" +
        "롐롑롓로록론롣롤롬롭롯롱롴롶롷롸롹롼뢀뢈뢉뢍뢔뢨뢰뢱뢴뢸룀룅료룐룔룕룝룟룡루룩룬룯룰룸룹룻룽뤀뤄뤈뤌뤔뤘뤠뤡뤰뤵뤼뤽륀륄륌륍륏륐륑륒류륙륜률륨륩륫륭르륵른륺륻를름릅릇릉릋릌릍릎릏릐릣릫리릭린릳릴림립릿" +
        "맀링맂맄마막만맍많맏말맑맗맘맙맛맜망맞맟맠맡맢맣매맥맨맬맴맵맷맸맹맺맻맽먀먁먄먕먜먠먤머먹먻먼먾멀멂멈멉멋멌멍멎멐멓메멕멘멜멤멥멧멨멩멪멭며멱멳면멵멸몀몃몄명몆몇몌몐모목몫몬몯몰몴몷몸몹못몽뫁뫄뫘뫼묀" +
        "묄묏묑묘묜묠묨묫묭무묵묶문묻물묽묾뭄뭅뭇뭉뭍뭎뭐뭔뭘뭡뭣뭥뭨뭬뮁뮈뮌뮐뮝뮤뮨뮫뮬뮴므믄믈믐믓믕믜미믹믺민믽믾믿밀밂밈밉밋밌밍밎및밐밑밒밓바박밖반받발밝밞밟밣밤밥밧밨방밪밫밬밭배백밲밴밷밸뱀뱁뱃뱄뱅뱆뱉" +
        "뱌뱍뱎뱐뱔뱟뱡뱩버벅번벉벋벌범법벗벘벙벚벛베벡벤벧벨벰벱벳벴벵벸벼벽변별볌볍볏볐병볓볔볕볘볜볠보복볶본볹볺볻볼봄봅봇봈봉봊봌봐봑봔봘봙봣봤봨봫봬봭봴봵뵀뵈뵉뵌뵐뵘뵙뵛뵜뵤뵨뵬뵹부북분붅붆붇불붉붐붑붓붕" +
        "붖붙붜붤붸붹뷀뷁뷃뷐뷔뷘뷜뷤뷧뷩뷰뷴뷷뷸븀븅브븍븐블븜븟븡븥븨븬빂비빅빈빋빌빍빔빕빗빙빚빛빝빠빡빢빤빧빨빰빱빳빴빵빻빼빽뺀뺄뺌뺍뺏뺐뺑뺒뺘뺠뺨뺴뺵뻉뻐뻑뻔뻗뻘뻡뻣뻤뻥뻬뻭뻴뼈뼉뼘뼛뼜뼝뽀뽁뽄뽈뽐뽑뽕뽜" +
        "뽠뽯뽱뾰뿅뿌뿍뿐뿔뿜뿟뿠뿡뿨뿩뿰쀍쀠쀨쀼쁘쁜쁠쁨쁩쁭삐삑삔삘삠삤삥사삭삮삯산삳살삵삶삻삼삽삿샀상샄샅새색샊샌샐샘샙샛샜생샠샡샣샤샥샨샬샴샵샷샹샾섀섄섈섐섕서석섞선섣설섥섫섬섭섯섰성섲섳섴섵섶섷세섹섺섻" +
        "센섿셀셈셉셋셌셍셐셑셔셕션셜셤셥셧셨셩셰셱셴셸솀솁솃솅소속솎손솓솔솜솝솟송솢솤솥솦솧솨솬솰솻솽쇄쇠쇤쇨쇳쇼쇽숀숄숌숍숏숑수숙순숟술숨숩숫숭숯숰숱숲숴숼쉄쉅쉈쉐쉑쉒쉔쉘쉠쉡쉣쉥쉩쉬쉭쉰쉴쉼쉽쉿슁슈슉슌슐" +
        "슘슙슛슝슠스슥슨슬슭슲슴습슷승슺슼슽슾싀싄싈싐싑싖시식신싡싢싣실싫심십싯싰싱싴싵싶싷싸싹싼쌀쌈쌉쌋쌌쌍쌓쌔쌕쌘쌜쌤쌩쌰쌴썁썅썌썜썡써썩썪썬썰썱썸썹썻썼썽쎀쎄쎅쎈쎌쎔쎗쎘쎠쎤쎴쎼쎾쏀쏔쏘쏙쏜쏟쏠쏨쏩쏫쏭" +
        "쏱쏴쏸쐈쐌쐐쐬쐰쑈쑝쑤쑥쑨쑬쑴쑷쑹쒀쒕쒜쒯쒰쒸쒼쓉쓔쓕쓩쓰쓱쓴쓸쓿씀씁씌씐씔씝씨씩씪씬씰씸씹씻씼씽앀앂아악안앉않앋알앍앎앏앓암압앖앗았앙앚앛앜앝앞앟애액앢앤앨앰앱앳앴앵앸야약얀얂얄얆얇얉얌얍얏얐양얔얕" +
        "얖얗얘얙얚얜얠얨얬얭어억얶언얹얺얻얼얽얾엄업없엇었엉엊엌엎엏에엑엒엓엔엘엠엡엣엤엥엨엪여역엮연엲열엵엶엷엹염엽엾엿였영옂옃옄옅옆옇예옉옌옐옘옙옛옜옝오옥온옫올옭옮옯옰옳옴옵옶옷옸옹옺옻옼옾옿와왁왃완왅" +
        "왈왐왑왓왔왕왘왜왝왠왤왬왱외왹왼욀욈욋욌욍욐요욕욘욜욤욥욧욨용욬욯우욱운욷울욹욺움웁웃웄웅워웍원월웜웝웟웠웡웤웨웩웬웰웸웹웻웽윀윁위윅윈윋윌윔윕윖윗윘윙윜윟유육윤율윰윱윳융윷으윽윾은읃을읅읆읊음읍읎읏" +
        "응읒읓읔읕읖읗의읜읠읭읮이익읶인읹읺읻일읽읿잃임입잇있잉잊잋잌잍잎잏자작잔잖잗잘잛잠잡잣잤장잦잩잫재잭잰잴잼잽잿쟀쟁쟄쟈쟉쟌쟎쟘쟙쟛쟜쟝쟞쟤쟨쟬쟾저적전젅젆젇절젊젋점접젓젔정젖젛제젝젠젤젬젭젯젱젴져젹" +
        "젼졀졈졉졋졌졍졎졏졓졔졜졤조족존졷졸좀좁좃종좆좇좉좊좋좌좍좐좔좝좠좡좢좨죄죈죌죔죕죗죘죙죠죤죨죳죴죵죶죷죸죻주죽준줄줆줌줍줏중줒줗줘줜줠줫줬줭줮줴줸쥇쥐쥑쥔쥘쥠쥣쥤쥬쥰쥴쥼즁즈즉즌즐즘즙즛증즞즤지직짂" +
        "짃진짅짆짇질짊짏짐집짓징짖짗짘짙짚짛짜짝짞짠짢짤짦짧짫짬짭짯짰짱짲짴째짹짼쨈쨉쨋쨌쨍쨎쨓쨔쨘쨥쨩쨰쩁쩃쩄쩅쩌쩍쩐쩔쩖쩜쩝쩠쩡쩧쩨쩬쩰쪄쪈쪋쪘쪙쪠쪼쪽쪾쫀쫄쫌쫏쫑쫒쫓쫗쫘쫙쫩쫭쬐쬔쬠쬬쭁쭈쭉쭊쭌쭐쭘쭙쭝" +
        "쭤쮜쮠쮸쯍쯔쯕쯗쯤쯥쯧쯩쯪찌찍찎찐찔찜찝찟찠찡찢찧차착찬찮찯찰찱참찹찻찼창찾찿챁챃채책챈챌챔챕챗챘챙챠챡챤챦챨챱챴챵처척천철첨첩첫첬청첮체첵첸첼쳄쳅쳇쳉쳌쳐쳑쳔쳣쳤쳥쳫쳬초촉촌촏촐촘촙촛총촟촠촣촤촥촨" +
        "촬촹쵀쵁최쵝쵸쵼춈춉춋춌추축춘출춤춥춧충춰춴춸췃췄췌췐췟취췩췬췰췼츄츅츈츌츔츕츙츠측츤츨츰츳층츼츽칀치칙친칝칠칡칢침칩칫칭칰칳카칵칸칻칼캄캅캇캉캍캎캐캑캔캘캠캡캢캣캤캥캬캭캰캴캿컁컄커컥컨컫컬컴컵컷컸" +
        "컹컼컽컾케켁켄켈켐켑켓켔켕켜켠켤켬켯켰켱켸코콕콘콜콤콥콧콩콬콮콯콰콱콴콸쾀쾅쾌쾍쾡쾨쾬쾰쿄쿈쿙쿠쿡쿤쿨쿰쿱쿳쿵쿸쿼쿽퀀퀄퀍퀘퀙퀜퀠퀩퀭퀴퀵퀸퀼큄큅큉큐큔큘큠큡큨크큭큰클큹큼큽킁킄킈킌키킥킨킬킴킵킷킹킼" +
        "타탁탄탆탈탉탐탑탓탔탕탗태택탠탣탤탬탭탯탰탱탸탼턀턍터턱턴털텀텁텃텄텅텈텋테텍텐텔템텝텟텡텨텬텻텼톄톈토톡톤톨톰톱톳통톺톼퇀퇄퇘퇴퇼툇툉툐툠툥투툭툰툴툼툽툿퉁퉈퉐퉜퉝퉤퉨퉷퉽튀튄튈튐튕튜튝튠튤튬튭튱트" +
        "특튼튿틀틂틈틉틋틍틐틔틜티틱틴틸팀팁팃팅파팍팎판팑팔팜팝팟팠팡팥패팩팫팬팰팸팹팻팼팽퍄퍅퍈퍌퍙퍠퍼퍽펀펄펌펍펏펐펑페펙펜펠펨펩펫펭펴펵편펼폄폇폈평폐포폭폰폴폼폽폿퐁퐅퐈퐉퐌퐛퐝퐠푀푄푈푕표푝푠푤푯푱푸" +
        "푹푼풀풂품풉풋풍풓풔풕풰퓌퓐퓔퓨퓬퓰퓸퓽프픅픈플픔픕피픽핀필핌핍핏핑핓핕하학핛한핝핟할핡핣핥핦핧함합핫핬항핮핯핰핱핳해핵핶핸핻핼햄햅햇했행햌햏햐햑햔햘햠햡햣햤향햫햬햰헀허헉헌헏헐헑험헙헛헝헠헣헤헥헨헬" +
        "헴헵헷헸헹헿혀혁현혈혐협혓혔형혖혜혤호혹혼혾홀홅홈홉홋홍홐홑홓화확환홛활홥홧홨황홪홬홯홰홱횃회획횐횓횔횝횟횡횤효횬횰횽훃후훅훈훋훌훍훑훓훔훕훗훙훚훜훞훟훠훡훤훨훳훵훸훼훽휄휏휑휘휙휜휠휨휩휫휭휴휸휼흄" +
        "흉흏흐흑흔흗흘흙흝흠흡흣흥흨흩흫희흭흰흳흽힁히힉힌힐힘힙힛힜힝힞힢힣"

    /** 음절별 위치 로그확률: [홀로 | 첫 | 가운데 | 끝] 네 구간이 각각 POS_SYLLABLES 와 같은 길이로 이어 붙어 있다. */
    const val POS_TABLE =
        "vrp*>k7SdhhfGmS/VZ?4uUVdWaS1]*SU,=**i*YV*kA,pSle]*uSU.,.]11q`*`[/^Z0,/YcE^-_oZ7Fj-1S*i**20vjbnj2V.cg" +
        "mhaS3*,tbhDd,,d^*V29YUS-2Ae,.*ni**oec><*.U[jd6,SXUn[*5VS*e.YS3.S,[._^1U-wfUeUh:klUYS*US,oZm-kSn[[/W*" +
        "..ES_V>_*[WW,31X,?_,b_U*57U][SSS*SSS^UWU*S`@XS<-*^.6*.-*Z**5?3]m*XUc?D5S<g***Sd.bjX*4]S02*X__*b9h;8S" +
        "3****YS0=_*;ZVS,?<71S*.SD/SfU,b.>`0ZY*j.bS,dXU312-o[U*n*So;SlfaS[hY3V,BrSk*k[U[G^*.`4U3S-*^2***gaZfc" +
        "1S*SkUU5S.Dn_X-`::b6S6,hS*~/S4@USU11*h^bSaS*cUS*eS*VH^,-*2@-g,*Y4U9-*<`]j**XY994*,*@:UX@S11k0S1D*UaU" +
        "Sxj>*<.._U*bS*S*ee*Y-ec=UU*1Vr]<nSpeU-Uac*^*oS]SAu[eS`fU_9<*-S3****-vc/c,k.=`[Y-[-`B-s`b*h[^S.S4,U*7" +
        "****7.-S5.qelAgSUSfZ>m_,S.4SUk30X3J,],rqiAU<8X0**u]llSe;3]_,?S*rS.4-b*6*X-`A?S.m[hVj[***`Blx,*,2,idf" +
        "XbUf=6S>-**fjUima*5:i*Su-g*YV*38Z-**,]/S.*0db*YZ1/S89VS^Sab,*W5,/*SUt_.*UUSb.U*SSY.*Xf.*6-S.-Y*Uec4." +
        "SD<b<a/Z.gS*,Aa^S1*dUf_U,9mdj*^bYZFc*147]a_-<hfW?`0,UHS/*-,-,f*-,g_cUYW*SE[HjVcA`Z]3:*^YYSUFSIa*Z*;*" +
        ",**vjeSecc[e***4U-,*S*SSX,S2[2b-5*12[faa*bdXVW,S***=U***Z]S6/-**.*hU_UC,/SfSM**wS>SU*S,>*S*,lee*a`^S" +
        "*e*.fjv,XVq>,b=f-gY1-GS5iij[Sdf4`D.*S44,-**d[Uj,_*V0^/`6*.[^_aCSX,S**tZ*l*V/S9t1qS5hhba,e**hWm`S,,Y2" +
        "7/,e41*-.hYAkSl6*>*]?5Slhi6XUSS*USV*b5*S<SW<62,.n`-g*,Eh-cS_SYWx*dS1omelUjV.W,lhZ5i,*.`ojSfSUdSW,[*9" +
        ";-*3U02S_`qS/ebka*VS.^`gShW4X,S*cefmS>V5h**V.8,k`>p**SqkS_-dS*eSS**SV,-SS*,.;*Y[]6-/4S-*lhp**WgSb1_^" +
        "*G-S-S*ZUSZ16***eS*8<WY.SU-*0*.*-kjlSiSgU[adh*_X-W*]X/38f7aZg_V.;7S*,S_3S*VYd?Z,99^5*Se0U3,*W21S9S]S" +
        ",*,UYV6mcS2*7*S*U**WSB8>4*WXWWS,Ul[*Sn*m[g.b]V=l,Spi,^ZgUS2e,**f3[]]ba_U>2/24lkAn8i/*kYDDn*,.,2*tZ,S" +
        "d,hdSd6SS*Y-`UV,[ASS0=c35-Sim3i.d_SSj,0]0-1***5`_YS5hSdZ0__SzXh6gcSWW],7i7,*.1V0*V`*S]2S^/`d]]Z,aSaW" +
        "USaUS`W[WS*VZEl*,,---**--shmS,UhVhbZ-b**X*acgi]S*:cCU32*]ZS-*]*-*fa1e^*e95C8,U0_SU,,-*,S*S*VbdScSS-V" +
        ".`*4*U5*Z*S`V,.*YS*.Y*V,,USU`Zmk*aWS-*Sjh*c^4_<*[*-ndpB[*oS]*>hY.aZ_SS^,iSd[-laSm_8]*ftf*S,>*Z]Z,lS=" +
        "*7`.*]*,*-gs*a<,Gd=*`e_[ZY4]<,wV.*hgaYYU^-,os<o*k*S6,b]3]Wj**/8e3n/]SSWn2/kaoSp1E.W>cU*e*^,U/*SuUW`*" +
        "]18`IlWpWb9U2mSZ3,,.-*ic`VYY6*j52f^bSc*-_9X,]fSr|`*SS`.Z]b]5mU2S,qZb*d:S*a,bV*ibk_SU:bVc]5v/v-*SiiSS" +
        "d0.V.-1x3-Z*zZ*q,-0|V*Dhga__BS3/e0ldh@,r*db[SoU,*fj_`dU:/[*<,X0.***[*^V,Slju**SjS3q[[/m]SwSbe[`U8*`0" +
        ";,S/7KS-*,0**sip3bpUWm`V.-]d,S2*,S*0e5Y**2*,_YSS*S0,*,ralnSe=7v**c.-UB*.U.*c1bd.S*[]W-/YqS^]ZSe*-nd*" +
        "*k**2h8*ina_S*2>[Sb`0c2a1C*SWS5i*1cS.SS6<2**S-*[S*.**YYgXU^d*U*3..Y,,22*Uj-5Ud,V8A.U_,SUV,SSXd*S5/.7" +
        "S.-2*Y.*bV^YUYS-`V^<1*^<3sbgS*_*m[S9gH1**mk`VS9[6XS*S1*,*,ejphbcs1j*fS`S66]S,f-/2SS*.ocg,:<3;r*SS1*<" +
        "DS**jVY,*,**hf^ce:8aU-*-@<-U^,3.*[*V,*-.Wj5:;/n.*/d]g*dY*ahS[.*fZj*j^`S`-*[0eZ`a*a-:cUS***.fS]?Z^jd@" +
        "S*,,]^fYZUa,1]^[S/G**e`dfZ]UeS*SV[ZVU`ZWU=*<ZV,ce`eZYS_*S*UX*U.21*SVafW04XcS>0*S`Vtg*`6X-/,i`acd^]hS" +
        "jch*f9agcS`*cZV*=Sc96Y91**babdVSZ5a**`V``[S89S-*7S>gciZigVe3*.*SX.2-32*h__a]1-Y:*3*_*]*DZYU8S,Z7@.*[" +
        "_`:d*a/SX,6Se[a_ma4[g]Sk*hfhZS]^he*hY[_d1ZY-0***a^ag[^39^[^d^UZc]`*m`V,:hdhilkdS5]*/S/*VSW_6*i,S*1*a" +
        "ddi.g^`_***,SS3`,;7S]*SZSUfcff^W``**ie-w*-sX,U**kfe.h**U.Vpj,ASZaW]Yg0UZ,-.-*/.d*,*2ka_Sf,X5[[2.ZXY_" +
        "WSV0VVa^o[U]5Jn,]-paa*gUj_?h*W*lfe*dS8*h*S*U,8r[/*3*?Z*^.,U*uadSS.U*^S[W*S**U,Sd-*WW**-,_Y]eZ>3*g*8_" +
        "[*Y`U.Vb,fWV^S>._*g*/*a:Yfj`Y*Y**Svoo#blZ#qihagrlOLrc-tg_hY[`*c#[SLPL#[#ZO#reOpelmeLt0U(O(d+*od#a[LZ" +
        "[L%(ikijPrfeRPt&+b#r#LR*tjgekV+Ocelu[&-#%pes`^%%o^LgUZhXVPSer%(#st##oegb`#RXbejW%L]Rn^L.h#LlQZVL(Z%o" +
        "(g]Q)Lvm%nLn_qm[f#LP%%w[jLlLr^c)XL'Pi#k^b]#dcZ%QR`%9h%XT)LWZWUR#####T%eXfW#ZYcX]]OLX'0#'&#Z##/UOge#Y" +
        "LcahU^`f#LLWYO^_W#Wc%S,#iXW#c]gTYSL##LLVSOXY#_)&RL851*Q#O#bO&cg%j(b`QUSLmPgQ%TXV-+SLtjdLk#Vl`Lqhdcfl" +
        "`LcLgsLc#ZcZWeh#'ZLS,S&#[+###m_[[hSi#LmZY/WPhnfT&aY^g0L0%a:#sLLL9VLWO+#rjmLi##bSW#n.#pj^%OL+Z&g%#Y.T" +
        "2&#RmWk#LbO]QT#O#R4)a:0+*nO%O>L.ld#ZmbLTOLjgL_V#O#kb#eL`aUc]#+Rud`qepccOZmiLc#q&W1cxYd&^aSdYZ#&ZL###" +
        "#&niRg%c'7``h&dPXf&o^h#f[VU(Y-%O#0L##L1LPLT'sqjdn#P#`fas]%QOLY%fRO.Rj%r%ndabU]YR)#Ln]gj#]TLd[%_XLl#L" +
        "RLg#X#eOZO8#PogcgpR#LL[[es%#%Q%o_[]e[aO0]L&#LrdO`g`#W^h#XqLSLX]#PO_&L#%X)LO#QkbLXlTQ#Z]SLcPYY%LYYOQL" +
        "#OqfO#]O#dR%L##)PLe`L#RPcOL^#jZTWQLf6a`[RZQkL#Oe_P1TLe%_RRO[rbd#`b^VLc#*-LgZfO`ccVLd*%`LL(#OL&%`#&%n" +
        "chS[SLU>TLocfY^^Z,W#ZXYOQ?:BZ#W#YL%##pee#eaZkc###VS&%###%%_L[OWLa&.#*,^nb_L`^UV^%[###7O###bXQXL&L#O#" +
        "hSZR=%(LhLQ##XM78V#L%7#(#Lp]f#da_Z#l#LslsLqaqbLd[gOjnS&k#Lqjg_^`]RfiQ#].L%L##lk%jOi#eSgQcY#'ohfggV^O" +
        "[LLd]#m#hLQLrTiPUtoag%lL#i_oi#%%_UZR%j-T#O'rderfpY#[LXcX#hfaY[[%OL^c^Li.L[L`P[LOO(ta&oLOjlP`]_S]OU#g" +
        "#LroirqsoOcOgd^VrO#'_rn#g&ecW`LdLZ[OL-QSR#n_p&Rlloh#^_QofhWiTW[Oa#cfpm#TY.m##ULOOun_oL#Oke`[OkYLhL##" +
        "#`k%OOL#OL]LPZUYLQTL&#tor##SqfaOahLjL#O#LR%#`RYL#LeOLU5XoP<m&#PLP#PtiiLj#`_effi#l_OYLhRLS2dYh^X[RP_Z" +
        "YLL#]ULL[XedYLY3ZTL#c*LV%L`SOR]f[QO#O_VfLi`^,#X###%L#XZ;S7-#`QSV#LTwf#Tp#nThPpeaat%Xoj%gadL]SrL##iU[" +
        "a`]]aPbURUVtles[qRLjgW`s#%(%T#te%Oj%heZdTW%Lc&Y^PL];OfOW`SUPWtnVnQk`Val%)YPLL#LLWbf^LXjOZ]SW]PuinZgh" +
        "Ogf[%ZdOL#'*bQ#UZLPVQ#gO^_^jP%kQ]bV&]V#sPZj5LZhRn#%LOLOLLOLvns#Larfoh[&j#Lj#j``c]V#XigVTRLX[)OLT#&Lf" +
        "aT``LbLVgR%]RUT&L%&#OLL##fX[ecSRLTQ[#W#bXLS#_]a%L#^&LORLWOLL#%n]dcLZ_aPL#i[#_XPa`L^#Pvlqer#qOVLblkL^" +
        "UhO#OOn)okOgjag[2g#nm`L_OcL[VV%p,aL0gP#LLO#&roLo`OlkaLkorcXeWL_%rh(Llje_a%`&%sqat#pLLYOic-bht#L)]f,r" +
        "LadUSeURtim%pUiQObbe#e#f%YO#Ol['oLaT1cjo+iPb]LRqOgVLO'L#pgYZY[U#n.,r]p(kLLi[i%ekZrp`LPOW'iTadXiSR&Ot" +
        "Xi#i^P#b%_&LtlmfR*LgTmUXmLeOL]oaTSk)'*(L*s,&L#yi#u%PQuiLhpphxif%-(cOuqjTOkLkl^_rc%#reZRfV[Sd#_LW)OL#" +
        "#V#]&%#pqv##OmhVpm`)ucQu]db[XWZ#^OS%O)O_,LLLLL#tfnVkeeYpc]PPojLOR#L[LPgT*LLULL`VSLLSQO#%ummm#^_VtL#a" +
        "PLWb#POP#eSW]OL#aXW&LciSjRY%pLLvp#LrL#Ll[#eofiYL,ba%hfP`+^ShL]YQXeL*[#(ORSRS##SLLRLL(#L^S^_LSS#V#RL'" +
        "X%%R+#bgOW^WOX[eQSRL'Y'OO&YZ#SSL(VSO&SL]O#[&[OLchL^`_^QL^aVqjiQ#fLp[][nlS##nl[cg]^RdY#Q*LL#Logonhch*" +
        "oLoX^dYSUW%e&R+`O#LpicO[_O_nL#OSLTgQLLs]ZOLL##qngqc^OnSL#&S]L%n%SL#]#LO#&LclWLLQhLL(ndmLhX#ldZhL#q^g" +
        "#je_W_O#mRegjdL[O][L,##L'mLj8hlc_eO#%%lRdd]RY%RaXZO)V##p_mid^_fL##`OZT&Sd'O`L`^YOl_`cZTQ`LeL`b#dQRR#" +
        "Oe^bYL.TgLO)##pQemLYYV&(Ll_cg`_XcLofm#k2jie_b#ogZ#`^_V0cR*##ka_d[S]W_##mgdjbWSZOP#0Uanbdcd`VsV#'#/jP" +
        "T&P+#p[_cWQ&_RL,#U#O#hUW)[e%OZ9'LnqaLf#d)1R%Y#k`_aj^Q[q_LoLkad_V_Ynd#iaY^YTeWL*##LjWgdcX-3dn_gfZa_`c" +
        "#oh]L]qlrmckbSR`#RLR#S#XOZLoO#LRLj[_j(jQbkLL#O]TTdO[0Lr#Po9.oehoY]^dLLupLu#LlOOX##pobLo##ROQsl%e'`bY" +
        "cpp)TW%&PL#R(m#%LPn[k#c%cRbgR(lbgiX_^PYO`lrhhnWLoLjPpik#hRk`cm#[#pqoLp+[Ln#)#S%[qhO#ULcf#mO%R#qZj#f(" +
        "YLbLe]L##LV%YhL#'cLLOOiTVbUcV#k#X[e#jij'gaOah+j&b(kLd#QLl^eflbXLX#LLupq!QiL!ogg^jn[QNkXVomX_SW]SaLVL" +
        "L^!LULQ'LqZ%oWhjd!mYUP%'VSRneLcWPbnQ%PlpSm&o`bYiqNOWLs!!SSskfYiLQN`fis^N,LLqcqfNLNm]!]LZi.Q&QZrNQLsu" +
        "LLpXiSOL'X_`hVN%_)oc!Xb!!i'^ZSQO%lOi^ONLpm%mLlRpob_!!SL%vQgNi%e]bR^!'Nb!rZY^Lc[hNRQY%bd%RO)!RLWPQ!!!" +
        "L!P%eYTX!ZTa[U]L!g'XLN&!eLLXcRd^LTNb_bVXYc!!!SRLZTSL-S%)ULhVcL_Qa^XRVL!!!bSQaU!N)&g%`XXSLLN!gO&bXNhQ" +
        "ZcPaQ!bLjZNa_fVOR&sc[!oL,k4%ne`mcaV,Z%ZpZh!^_^XjaLLiSWUQ&Lf+LLLo^0bjObL%fO[XTLcodc&d[VgYS/%kaLmLlQdd" +
        "Y[QTLoaf%g!Ld_RLh.!ci]%&!Tc&cNLcWXZOL^kddL!_`V]U!%La^NedO*SjRLRe!Wi_!sjV!`NLpa!VbL&!tgLh&ei`bfL*PugX" +
        "qcnaYLTmi!dLqNN0au[`NV]_`Z_L&VS!L!!Nmj(i%hL[eZUNb&S_OobgLk^Y[QZ,%^LO!LL!ZO&NU'unfak!L!_ZZsS%'LRP%eUQ" +
        "NUm%uLpcecX^VXR!!nbee!`_Ug^NaQ!b!LVLXLSLd&1c`!Lskj_vZL!!gegn%LNSNpcd]b]X`YiaO!!gbOa`ZLTRb!LjLX!O`LUZ" +
        "ZN!L%ZN%OLPea!`c*(!YVQh`NNTL!RN%N!!La_LLZL!`'%!!!(L!a_O!Y&['N`L`TP-'%f^X[cL^'eL!%YL-Z)!`%OQL%VvmoLfp" +
        "g^joL*UZqgo&WjckceLLbk[PLLLLNnLONsgkgil!`iblrjjdc]bV]!rrnhghdmoLlL[!%LLvon!geijf!!LQ*NL!!LLLh%ZSUSoN" +
        "ULSTgpef!bej^a%h!!LaTLLLcVXUQO!LOLoidleNPet`lL!ioabgL%%`LLLLwknLimpf!jL'rkpLgNlWLa_cLofL&^!XohgUT``V" +
        "iZ'LYWUNNLLmg%j%_LW)`NcPLPo_hc`N]%Z!!bV!r!fLO]rLaQVpm]hNg!L`Xgf!LNX+Q(%fO)!LLqcWs]q.!`!`]L!a[VQRS%&!" +
        "^[V!gW!e]kX^WT%NrfOs!%]k&ZVejfO]!]!SplhqmpYO]%acYVp%!'ank!dNb_T]Lg!Z]L!ULLS!pblNLjlo_LZY'nak-iUNa%[L" +
        "chln!bRWnLL^NX%sn`p!!&h__eLkP!h&!L!ah%LO%L%OZ!PQ-UNNT&OLulqLLPkWZP^d!h&!L!!L%!ePS!!!fU!Z]Tn'dk&!O!LL" +
        "&s_gLh!`U`gWdLf]LX!cL(T[aUa]SNSLUTU!L!UR%!bVaTY%Z]TV!!`SOLN![QRQP]]N%L%LO_Sha]ULV!!!%!!PfbX`VL]ONV!L" +
        "NudLSs!kNYLh]]Tt%VilN]XaOZRqLLLgR^[Y_]['U*(LRto^r1oL!hgchtLL'NN!r^N&lNgedeXX%!hLmfR%YeQeN`^SW&RrpNj'" +
        "h_YdnN)[N&Q!!!QhcNNLh&VS)^SWtilOodS]fT%L^YLLOS`NLPX!(OT!gNYXYe+LjLWccNY]!vWil_!foioL%LLLL!!&&vqr!LRp" +
        "`pi]Oh!!gLg]]^XT!]dYXRO!TTP&!Q!N!c]N]Y!_ZV`Z%]NTULLNNL%O!!!_SRV[ONNV'T!N!ON!S!_YQLLLSL!NO!S%%O!%jT_b" +
        "!dWY&!!ka!bUV[T!Z!&tmpbf!hNL!YjiPeql'!R%bPklLh`ZcU[a!pod!_%N!XP]Nq,L!Z_LLN!%LNui!nY%WgU!hsmhu`OY[Nxd" +
        "'!ihcW_%Y&NpqSq!l!%/%jeV^tr!!(1ZUmQ`VO_X+Nqil%j*V'LP^cL`LeNR(!'nZ'i!_NV^llNdLVSNSn&RQ%%PNLqi]ZZQR!tL" +
        "+rdpOo!&lYgNgnbtkW!YjU'mQ[dPbPS&%r^d!]RXLaN`&!qmhmQR]m+x]SoLm&!NqfRQl)LN'&LrN&*LylLuL&Pr_!YppdndZ%PQ" +
        "aQvqhd%d!dk]YuVNLq^T.cO[(nL[%TRL!LLV!S&L!mttLL&mULpj[Ru[LtiggZV]U!kQ]NNQYoTN!LQ!!rolNcbZTo^NL&fh%LNL" +
        "LU!QhQN!!*!L]WL&!+P%!Ltepg!a^Xp!LcLNVd!LLLLcOXPONL`PP&On[gch]Ln!NwoL!q!LToO!emgk_!+P[%hbOWTZNX!XQNPb" +
        "!)i!QLNX_RLLRL!TS!QL!e[[`&XNL^LSLPVNNS+LXmLQWU%ST^'OP%NTL%'LY^!LUPQW[LOQ!`O!a&VP&deNZ]Z]Q!ZUNoljgLl!" +
        "hUUYldQ!LjmV^[RZX[]LVS!%LLnhnlfdWSo!rVcaQYSR%iNLTjO!Nngh%Z[TRl!!&N!^cQ!!kUX%!LLLlmgpfS[jeNLOd]L%iNNO" +
        "L`!R%LOOlkN]S(j'!Nsjj!g.!k`^kLLpSgLg`_X_LLhOc[f_!]LXY&LL!!Oj[gbggfeSYLNLmNbd]VhNOgXSQRjLLo`hhfb_fNL!" +
        "aNZQLYe'TY!Pf.%lY^c[VOZ!c!`[Lc'TQL&dTZ]OW+gNbOL!qLak!dNRL(Lp_iebZbf&rilLmZdd[ZiLonf!]`ZZYc[QL!q`ihbZ" +
        "NTW!LohkikaWW[&LYRYpdfi_cUq,!L!YjLPLUQLnXdabPN`Q!V!SL,La-SOQiN]SaN!tkf]k!_(ZT%Q!pfgfiXSip_bn!f_]][d]" +
        "kfLcZ[SRNcTNQ!L!lZf_`UV]`nefdT[[[eLlQQLZmhpiijhSV^LL&(L)!V-L!q%!!Q!hW^eOpL_i!!L%^LQk%]W&qLfnbWohfjZ`" +
        "^h!!ysLu!NmL%W!LopWPn!LNLTsgN^LP_Tbvr)WWL&LNLLQo!L!SiRj!_%nSWUQPgZgeZQUQPQhkogamTmoLh&pfjLg(c^Vg!0!u" +
        "kn!lTS!nL(LLNLsmN!N!Y_!jLLT!nVi!ZQ/!VLSZ!!L!O%_WLL'a!!&%iPLYL7,!gLYN_!ha`Ob^%[eRjLNNk!YLO!lOebbYS!YL" +
        "!(xmqLLj1#jdgZAka)TR8-ljVWQUQ*Z#S*%_##V#0(#nV%nLhfW#fLUL%R9OOuf#aWL[SOOOhm>h&jbV0PnLS8#qL#+*yjg`g,Q'" +
        "`dbn^&W#Lvaod5L%iY#=+3f.O&LXp%(#rs##o[g75#(TYaf/%%[)n`#.^##e'^VT(LOkOkZRPLlj%nRiLomXY##W%OvLj&k%e5_)" +
        "_#Q(?#lT8b#_TZ%U*WO_aOT/R#.1RVL#####,L^T;WL]V:b2]&#oQW#OPL_##/LT^Y#V(aRYLZ5cL##SSLXOT#-PL*L#bVd#b3[T" +
        "SVL#L##aQ)7ULL)&PLa_X*O#'#XLObL%a(8d)UR#[LdS%bcd-TLOubL#p#Ln5%laXP`ZSVXLLo^iLg_YQAZ#PkVYLSP#iU###iV1" +
        "`k*?#%bO_/R'>mZ_PbYOj0]YOic#oRgWLdXTRL#mYd%_##aPP#bXLEO`OL#+9P_%#a.SX&#]cSb##b`URQL%#b4PX]ZT*d*%Lf#P" +
        "i:#{h7#U'Pk<#V_#&Lof#kOdhYcg#TO|fLnPm^9&1jc#_#p&/ZTs[]&Z_UV2Z#PVSL#LLLjf(rO[PadT[LX&P;&rZf#i^YY([WOX" +
        "#Z####1'&OVQwlePg#L#_LSpOO(LVS%jP)W,OOdLsngL05WR)L#ncfb#^QPd^%8L#`#O.L<#/#ZLOba#'rho=pZ###b`iiO#%O%k" +
        "`dOb[Y]0h7&L#b_Oa`W#L3^LLkLa#/V#L1VL##OZR%(#LZ]#kU*O#OL,>_LOPL#//%(##(`VO#WL#Z(%###)'#V_'LO&LP&W#LTT" +
        "O'%]^^6[(['^LL%;5-L*#^%Z/L%Xsjm#ckd[Qk#TSLnfgLVic^LaSL_iUO#&%O%m#&%pdmfml#bL^Ajaf`b]d,VLnomfbeWSm#j#" +
        "]#O##zpm#dc^beLL#POLLL##%%cLRQXRmLV#*Rflbe#cdjW^O_LL#7R###YUTTL&##(#mgaje%O^n]q#LylS^e#%O_#P#%tio#jk" +
        "meLj#Qokt%O6mL%_Xa&lO*PL#/lffV3`^.h>'#S.S%&##hZ%g%]#WLY(_/#'eXc_LO]%S##uTLtLaQO3n+f*SmjXf%f##[Tdc#L%" +
        "S+1(OcW*L&Poa;n<n/L^#ZL.#_SU021L&#WYL#ZQ#P_ZXVV,%Pne&l#%Of&UQaUh*]LZ#*kiXnSmD(9%acU.m%LQ^ig#Y&W]PZ%a" +
        "#3R&#R))+#kZm&(mjm]#U5'f`a-hP.a%V#Yfil#L/.j##WLZ%nj7o#L&hcOe&h0#cL##L8LO&[%#%'T#OQ.0&(SL&#sjm##,f;[Q" +
        "Va#RO#&##O%#dL/#L#eX#1]LmLeiPLO#'#&o_hLe#]PbeVb#cV&R#]OR-2aOSOR2O'500#%#3,%#[Z_9WL23R.##_*QL%#ZQOL2<" +
        "[)%#%4TSYf_S,#TL#L%#L/LeY`-#ZL+T#%Ls_#OpLgRS'eWS6rO0fk%VP_([+n%##eO`[Wa_Z(7+(+Rxm:q1m(#gfgLq#LR%LLm]" +
        "%&g%fbac/UL#bOngSL3;U^QZbR/&.po-g'd]XUl%SZPOPL##LfaL'/f&UPL`SXsgi0m^SL]RO1_T%#L*^P#LY#PQ,#cPXUO^PLfO" +
        "U]d&Y]#uOkdX#dh[k#O%&L&##LOspo#%6lLlgZ&gL#T#_Zf^Y.L3_=P,R#SRPL#OLL#kY+_Y#_Z/=TO_)TR&%%L#%P##L]WP;VO)" +
        "&X'RL.L7/#SL]YPLO#TL#'+#P%LL#%dR_^#`5Q&##khLhV.SL#YL&sjp;OLdSO#7hf(]ThO#V%^Oii&`[RaO2^Lqmb#^%8#YQU%m" +
        "V6#0T'#,#%#Lue#j6%Lh7#iqVaZ^-Z5%zXR#kiaQ_%YP%sp6lLj#%/%gd-WVnL#S2SQi)aWQ5O,)ogmLh+P')7bS#_#e%0RLLuX'" +
        "f#XLZZClR])R2(,l&P-L%'&#rgXV_UXLpXVrkpOo#OiPe%fjTskU#PPWQ_WZbOZSLO%o^dLZOP#^%]&#mjgkR*Ri+^V/yQzL#2pg" +
        ",PgSPQROS{VPL#yi#vL&)q>#>nl`Sf;LVL_)tmd:%_#__Q4sS%#o]SOdLL)l#WLP)L###SLP&L#mrq##&k=,nk[)qX(rSfdYQ^PL" +
        "mO]%LO0OR&#%L#Lnlk,ad:LoWL'&SeLLS#%W#*fV*##+#%iZ*L#T)%LLqanh#`ULm##b'&/;L'P'#b*[T('#]RSPPnYjahZ%j#&v" +
        "m##r##OnPLejchL#U7TLkePT+Y*=#QS)/b#Tn#('*/5L##OL#VU#(##T`Ra&]S#V#SP'U%%+U#Tk&R4V%S1:'+RLL0'%OLXYL*SL" +
        "(USO&R#](Ld&ZO&cb&ZRXSL#WR,ofgU#i#dQ32hL+L#hj[WSOZ/U]#W*#L#%jelkbcP*l#pWa_P0WLOlLL+OOLLkbh%LOQOf##&*" +
        "#]UL##g3Z%#%##gkhnb42fg&#&:YL%d%U'#]LU%#&'lhSU_OiP#Qpgj#g/Lga_iP#mYf#dY[XZ&#[)_P[]#]&VW&U#L#LiXd8gei" +
        "c:W#%LhO`cYLg%RiULL)L##lagfe[bfL##T,PS&W[',R#5g/%hX]cZVOY#R#OX#S',P#&`Q[RRQ+fVTR##pQbg#kLUOR%m^ihaTb" +
        "gLnej#gY^cY5d#jjZLXQ[W0`SR#Ls[jkcQL.WL#hechjdZWV&#0RYmeg^`dRm,LPL/f'+OLT#iWe[^OL_^#,LU#R#YP/P1]%W0bO" +
        "#sZgLgLYS1LOL#lghbhYUjl]]l#da_`3bWfa#aL^SP+^T&QL##iY[Y^VO3`g^a`SR_S]#m=2%4ggkhjai,U_#(L(#*#L.0#m%##*" +
        "#bZ_cOn-[f###%R+OV%OXLn#hh_.kjffZ[`h##mpLxL&sT%0L#lmX(kL#TLUvd%XOL`P_WoSVULP'&#((kLL#+eTi#WOkWWVSL`Q" +
        "b^[4T)RRiimc^g/Lo%g&odg#c)``PeL1Lr^l#h+1#k#R#P%1qiOLL#8WLeLLTLqVi#V(/#V&WX####RO]UL#'[##L%eS.VL8-Lf#" +
        "WSXLea^La[%_`QfL7Pk#R#)#rLhdePW#V##R"

    const val POS_LO = -29.529995415066036
    const val POS_HI = -1.9951437636340488

    /** 표에 없는 음절의 위치별 로그확률(평활 바닥값): 홀로, 첫, 가운데, 끝. */
    val POS_FLOOR = doubleArrayOf(-28.42529297122015, -30.422759691847247, -30.628607703734147, -30.422759691847247)

    /** 어절(한글 연속 구간) 길이 1..12+ 의 로그확률. */
    val POS_LEN = doubleArrayOf(-2.1247479991853235, -1.2654428992496638, -1.1984920571627031, -1.7145430790286604, -2.565309365097587, -3.716396283199078, -4.740457068628106, -5.632372510990044, -6.46041170247553, -7.218960300864873, -7.877394626763026, -7.5609178826318475)

    /** 자주 쓰는 어절 상위 1000개(쉼표 구분, 빈도 순). */
    const val EOJ_WORDS =
        "년,월,일,있다,수,이,있는,등,의,는,및,를,그,대한민국의,제,대한,한,에,위해,을,이후,가,같은,더,중,것으로,은,고,한다,로,통해,다른,가장,만,그는,함께,대,했다,또한,영화" +
        ",두,위한,따라,와,년에,때,미국,전,많은,때문에,에서,개,되었다,후,또는,같이,것,그러나,것을,약,서울,것이다,선수,큰,미국의,모든,말했다,이는,그의,하는,등을,세,지난,일본,당" +
        "시,배우,그리고,있었다,번째,경우,있으며,며,밝혔다,이라고,명,된다,다시,또,것은,다양한,특히,대해,여러,모두,세계,과,것이,따르면,으로,현재,주요,의해,차,이러한,개의,등의,첫," +
        "데,국내,할,이를,억,새로운,하지만,보기,등이,외부,동안,라고,시,높은,일부,조,대한민국,일본의,올해,중국,이번,역,이상,너무,없는,하고,최근,아니라,없다,링크,지난해,이다,이에," +
        "최대,한국,많이,축구,자신의,기자,총,관련,내,각주,잘,가수,회,라는,글로벌,인해,원,하였다,억원,뒤,이런,년부터,이어,매우,한편,역시,각,있도록,정말,일에,영향을,명의,있어,크게" +
        ",가운데,있다는,년에는,않고,세기,사진,된,지역,대통령,들어,다,년대,있고,주,도,거의,될,않았다,가지,게임,않은,보다,직접,가지고,기준,대비,주로,관계자는,진짜,때문이다,서비스," +
        "다음과,년까지,각각,결국,있다고,만에,보고,정부,어떤,시간,위,프랑스,정치인,번,영국,경기,독일,다음,않는,지난달,월에,예정이다,이날,시장,중요한,볼,최고,역사,러시아,인,즉,유럽" +
        ",명이,분기,공식,받았다,기업,같다,경제,전쟁,일부터,열린,있을,전체,일반적으로,않는다,없이,기존,이라는,따른,디지털,더욱,좋은,사건,이미,수도,게,몇,있던,결과,점,등에,역할을," +
        "말,아닌,개발,계속,오는,설명했다,따라서,해당,코로나,작은,동시에,해,되는,처음,등으로,중심으로,대표,초기,로마,관한,있지만,천,되어,계획이다,평균,기술,가진,안,간,기간,국가,국" +
        "제,조선,서로,기원전,왜,정부는,그가,마지막,이상의,개월,제공,시즌,사업,예를,있는데,최초의,일까지,추가,새,만원,받은,드라마,만큼,야구,년간,이라며,시작했다,처음으로,대부분의,서" +
        "비스를,기타,만든,에너지,있게,역대,영국의,분,달러,보인다,지원,해외,뉴스,데이터,대신,다만,이름을,사이에,리그,이렇게,대상으로,호선,반면,호,가능한,좀,실제,투자,오후,반도체,비" +
        "해,감독,필요한,문화,포함한,대학,올,가격,본,정부가,바로,달,이들은,건,프랑스의,우리,존,기준으로,세는,보면,사용하는,최초로,그냥,독일의,사망,달리,전국,최고의,현,도시,신규,여" +
        "전히,가능성이,초,교육,알려져,받고,배,대부분,수는,먼저,시스템,교황,것이라고,연속,받아,온라인,온,현대,플랫폼,두고,음력,인구,어떻게,물론,정부의,하여,낮은,어느,상반기,위치한," +
        "연구,여,포인트,과정에서,기록했다,후에,됐다,사람이,당,월부터,하며,특정,까지,컴퓨터,사이의,고대,오전,것도,시대,세의,날,운영,발표했다,바,거쳐,없었다,불구하고,삼성,이름은,연합" +
        "뉴스,혹은,성우,일반,출시,비롯한,않다,의한,아니다,하지,그를,완전히,전에,한국의,나,이유로,정도로,통한,작품,인한,기,제공한다,바탕으로,이탈리아,이전,대전,많다,앞서,자동차,내가" +
        ",보는,군,모바일,회장,기술을,중이다,시리즈,기능을,알,가능하다,금리,위에,중인,이와,알려진,이들,기반,활동을,하나의,자신이,지역의,하반기,아직,명을,연,부산,되고,겸,공동,아들," +
        "사회,제품,인터넷,산업,문제를,정도,브랜드,경기도,사업을,쉽게,사람들이,사용,사용된다,장,참고,각종,아파트,대표는,문제가,사용할,전자,사람은,기반으로,위를,점을,한다는,이것은,사용" +
        "하여,나타났다,강조했다,원래,정보를,향후,그룹,받을,갖고,널리,보통,빠르게,있기,있습니다,대규모,사회적,걸쳐,나온다,훨씬,목록,중국의,교수,관리,판매,말한다,만드는,시간이,하나,정" +
        "치,생산,불리는,세가,삼성전자,종종,제국의,대형,방송,이때,개인,영화를,모델,부동산,실제로,경우가,주는,수출,네,단,하다,프로그램,위하여,지역에,작년,중에,간의,못했다,매년,앞으로" +
        ",물가,년의,뉴시스,사용한다,음악,않을,정치적,난,규모의,김,고객,인도,이란,그들은,전기,회사,그들의,제공하는,것과,상승,정보,수도권,수가,확대,것에,월에는,못한,방식으로,월드컵," +
        "성,있었던,층,적극,그대로,줄,기본,등과,시절,활동,국회의원,나는,소수,소프트웨어,집,때는,문제,전했다,일을,비슷한,곧,영화배우,전용,완전,정책,대상,탄생,현재의,최종,러시아의,사" +
        "람,핵심,영화는,매출,대표적인,자주,번째로,일에는,전년,미,그런,일반적인,공화국,왔다,전문,세대,한다고,없고,사용되는,자체,콘텐츠,여성,환경,선거,형태로,권,대해서는,명으로,관계," +
        "제대로,출신,아버지,관련된,전혀,선,지금,있어서,지원을,이제,등은,않아,대학교,모습을,그리스,아,관심을,아시아,강력한,사실상,표준,만들어,교수는,구,때까지,지역에서,금융,윈도우,행" +
        "정,나머지,수많은,강한,라며,인구는,작가,못하고,것이라는,변경,되었고,계획,홈페이지,과거,알려졌다,예,그래서,왕조,소속,연간,유명한,제국,존재한다,주의,나중에,영어,지방,월까지,외" +
        "에도,언어,억원을,에서는,정책을,안에,가구,군사,데이터를,것이었다,동일한,영화가,우크라이나,이달,가까운,이하,가격이,대하여,관계를,세계에서,문자,진행,옛,국회,주변,하나로,상품,네" +
        "이버,마찬가지로,아래,지구,항상,과정을,더불어,규제,받는,오히려,구간,내용을,올랐다,올림픽,분야,기록,지역은,새로,나오는,에는,하루,형,내년,비디오,영상,지,점차,전투,생애,기후," +
        "어려운,밖에,스포츠,통합,있으나,산,고려,업계,끝에,수상,스마트폰,라,과학,나온,여기서,인기,주장했다,일이,결과를,만들었다,유일한,회장은,억원으로,이며,제외한,스페인,힘을,시간을," +
        "제품을,있었고,발표한,오른,사상,하면,등에서,못,내에서,대구,기업의,사실을,자기,넘는,철도,지역을,최소,사람의,있지,비롯해,참,계획을,보였다,시스템을,시작한,이건,스스로,박,사람들" +
        "은,구성,강,대통령이,운동,긴,장관,우주,내부,여자,수요가,채널,빠른,구성된,대회,높다,이탈리아의,쓴,독립,현재는,아주,베트남,연기,기독교,채,학교,남아,설립,조지,어린,자료,인기" +
        "를,구조,팀,앱,이게,봤는데,성장,이야기,교통,상황에서,애니메이션,건설,신라,시기에,인천,제도,뉴욕,딸,대출,년대에,만나,오늘,의하여,사이에서,상대적으로,젊은,꼭,의미한다,우선,시" +
        "장에서,지속적으로,이유는,제작,통신,개발한,그동안,캐나다,사는,황제,시작,사고,세기에,시장에,뿐만,자유,여름,유사한,상대로,말을,프로그램을,서울특별시,주년,전망이다,현지,인상,필요" +
        "하다,뿐,추진,초대,시대의,여기에,단지,규모,직후,사장,목표로,경영,도움을,가는,네트워크,이름으로,실적,방안을,속에서,시대에,미래,지하철,상품을,인간의,최초,스토리,측은,오늘날,카" +
        "카오,할인,차이가,비교적,폴란드,곳,목적으로,웹,해도,의하면,이로,떨어진,군인,속,알고,없어,기업인,차례,하기,만약,종,외국인,조사,발생한,신,중에서,보이는,강화,비트,점에서,아프" +
        "리카,조원,상당히,프로,길이,이름이"

    /** EOJ_WORDS 순서대로 어절 로그확률. */
    const val EOJ_LEVELS =
        "~urojh``_^]ZYXXXWVVUTTTTTTTTSSSSRQPPOONNNNNNNNMMMMMLLLLKKKKJJJJJJJJIIIIHHHHHHHHHHHGGGGGGGGGFFFFFFFFE" +
        "EEEEEEEEEEDDDDDCCCCBBBBBBBAAAAAAAAAAAAAA@@@@@@@@@@@???????>>>>>>=======<<<<<<<<<<<<<<<<;;;;;;;;;;;;;" +
        ";:::::::::::::99999999999999998888888888888888888777777777777766666666666666666666665555555555555555" +
        "5555555544444444444444444444433333333333333333332222222222222221111111111111111111110000000000000000" +
        "00000000000///////////////////////////////.................................-------------------------" +
        "---------------,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,++++++++++++++++++++++++++++++++++++++++++++++*******" +
        "***********************************************)))))))))))))))))))))))))))))))))))))))))))))))))((((" +
        "((((((((((((((((((((((((((((((((((((((('''''''''''''''''''''''''''''''''''''''''''''''''&&&&&&&&&&&&" +
        "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%" +
        "####################################################################!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"

    const val EOJ_LO = -9.359927672860726
    const val EOJ_HI = -4.119828542241656

    /** Shift 증인 사전 643개(쉼표 구분, 사전순): 한국어 글에서 Shift 가 무의미한 키에 대문자로 쓰인 적이 있는 라틴 문자열 중 판정에 영향을 주는 것. */
    const val LEX_WORDS =
        "abcl,abcp,abdl,abeek,abtb,abth,abtn,abvh,abvk,aha,ahah,ahci,ahd,ahdb,ahdl,ahf,aifb,aka,akel,aks,akt," +
        "ala,alc,alcm,alf,alfu,algo,alr,als,alsb,alt,altl,alty,amcl,amcp,amgn,amtek,amzn,andy,anfo,anr,ans,an" +
        "sel,ansi,anti,aoa,aocnqo,aod,aorus,aos,aosp,aotj,apap,apcp,apq,aprn,apru,aps,apsk,apt,aptld,auc,aucl" +
        ",aud,aus,auth,cbgk,cbrn,chaka,chan,chanels,chaos,chapel,chch,chcl,chco,chkdsk,chr,chro,chs,cjd,cjeu," +
        "cjr,cjvk,ckd,ckdb,cla,cld,clf,clr,cls,clsid,clstp,cmd,cmdi,cmgl,cmr,cna,cnd,cndp,cnr,cnrp,cns,cnsmdl" +
        ",cnsmdp,cnto,cnzm,coap,cocl,cogo,cor,coreos,corfo,corn,cos,cpan,cpap,cpcl,cpdna,cpfsk,cpgb,cprm,cps," +
        "cpsp,cpsu,cpt,cptm,cpttp,cucl,cucn,curb,cuso,dbc,dbd,dbf,dbr,dbs,dbsi,dbsm,dha,dhc,dhcp,dhd,dheh,dhl" +
        ",dho,dhr,dhs,dhtml,dia,dick,dicm,diem,dircm,dirco,dis,disk,dism,divo,djs,djsi,djvu,dka,dkg,dksh,dkw," +
        "dkz,dla,dlf,dlr,dls,dlsu,dlt,dlvo,dma,dmd,dmf,dml,dmr,dms,dmtn,dmz,dnd,dnf,dnp,dnso,dntp,dnvp,dnxhd," +
        "docu,dod,dodo,dof,dogma,dos,dosb,doslfn,dot,dotch,dpa,dpcm,dpd,dpf,dpr,dprk,dps,dpsk,dpt,dual,dur,du" +
        "t,duty,duv,ebs,ebsi,ehc,ehf,ehr,ehs,eht,eigo,eismd,ejsm,ekd,ekg,elf,elr,els,elsi,elspa,ema,emd,emf,e" +
        "mls,emr,emro,ems,emsb,emt,emtek,ena,end,endif,eneman,enf,enfj,enfp,enj,enr,ens,ensem,enso,entj,entp," +
        "eod,eof,epa,epems,epfl,eps,fbf,fbr,fbs,fha,fhd,fifm,fispt,fitl,fjd,fks,fktu,flak,flash,flq,fmso,fmt," +
        "fna,fndb,fnf,fngu,fnl,fnr,fns,fod,forb,fos,foxp,fpa,fpcb,fpry,fps,fpsb,fpso,fpt,fudan,fur,gba,gbd,gb" +
        "fms,ghci,ghd,ghdl,ghq,ghrh,ghs,girl,gks,gla,gladiator,glaha,gld,gls,glsdb,glsi,glsl,glxp,gma,gmd,gmf" +
        ",gml,gmr,gmsk,gmt,gmti,gnp,gnrh,gnso,gnz,goa,gof,gogo,gogoeigo,gogogo,goq,gor,got,goto,goty,gpd,gpep" +
        ",gpg,gpr,gps,gpstp,gpt,gud,gus,qhd,qkd,qlc,qms,qnan,qnd,qnt,qos,qps,qpsk,quan,rbau,rbs,rbt,rha,rhaps" +
        "ody,rhcl,rhd,rhdl,rhealth,rhel,rho,rhs,rhsm,rht,riau,ridl,rir,rjfo,rjr,rkf,rks,rla,rld,rlq,rmr,rms,r" +
        "na,rndus,rne,rnf,rnp,roa,roadmap,roan,roci,rock,rocks,rod,ros,rovl,rpa,rpf,rps,rpvm,rucl,rudp,ruf,ru" +
        "q,ruqoa,rur,rus,rush,sbcl,sbd,sbsi,sbsm,sbsu,sbti,sbtm,sha,shanels,sharp,shazna,shd,shf,shk,shrm,shs" +
        ",sid,siek,ska,skc,skdb,skf,skr,sks,skt,sktcj,skti,sktkdb,sktkt,sktms,sktsk,sktsm,skvm,sla,slf,slfp,s" +
        "lgb,slr,sls,slv,smawk,smcu,smd,smej,smf,smtown,sna,snan,snap,snel,snl,snp,snr,snrna,snry,sns,soa,soa" +
        "p,socks,socl,sod,sof,sofm,sogo,sorl,sorn,sory,sotp,soxl,spak,span,spdif,spdy,spf,spr,spt,suek,surl,s" +
        "us,syfy,sysk,tbcl,tbd,tbf,tbq,tbs,tbt,thek,theo,theory,thf,thk,thl,tho,thq,thru,thsi,thx,tia,tick,ti" +
        "cl,tif,tisi,tkf,tkr,tks,tlcl,tld,tlen,tlf,tlr,tlru,tls,tlt,tltco,tltro,tlv,tma,tmao,tmap,tmd,tmf,tmr" +
        ",tmrna,tms,tna,tnc,tnd,tnf,tnr,tns,tnt,tod,today,tof,togo,torgos,tos,tosel,town,towns,tpa,tpf,tps,tu" +
        "dn,tuf,tus,tytn,vhdl,vhdsl,vhf,vhs,vidp,vla,vlan,vldb,vldl,vlf,vlr,vls,vlsi,vlsm,vlt,vmf,vmro,vna,vn" +
        "d,vnl,voa,vod,vofan,vogl,vor,vos,vprj,vps,vpvb,wbs,wha,whc,whd,whdh,whflq,whk,whl,whrb,widy,wkw,wlan" +
        ",wltp,wlvi,wma,wmd,wmf,wmfo,wmt,wna,wocn,wordml,works,wos,wpa,wpch,wpf,wps,xhtml,xjr,xjs,xkr,xls,xma" +
        ",xms,xmt,xna,xns,xor,xoxo,xpcl,xps,zbrush,zbtj,zhao,zhdk,zhg,zhr,zht,zirp,zmd,zncl,zncu,zozo,zpr,zpt"

    /** 소문자로 쓰일 로그확률(소문자 출현 + 0.2×대문자 증인). */
    const val LEX_LOWER =
        "!92).!)!!90!)!)!)@!7:57.))!0D!H.!.!),),)!D!HKA!,.@!!!.,!!B!?.),7F).!)A!7!!)!,,!),!!!?!,!4324)=.!,!!," +
        ",,L!!!!!)!<2!)<.!!!)))H))3!))!3))6.,A!)84;!!?!!!7H2).)!>>!!!:)>!!)07E6K!3!C2,!<0,L3E.)0!!!4287V!)G!9" +
        "5):).?!!B727A`A!)3,.!!.!)B.R!!>.7!E!A!!!M_!,!),!!@!2)..)H!!K!!3!7!!!!!!.?)!)44))3!9!!!!,5!Q!7)!8<5!!" +
        "!!C!!H,..!!C!,)!>2!!2)@)A!!!!.=!!!!JO,9!))W!>!27F,3.!)<!.!!4!)!!!!!;7!!)!!<!!,!!!,GHY!!,)6!!)K77A!G<" +
        "7!!!))).77).)!!3)G!?)4)!8!77=H!,,5]46!)!0!!4)),B8!!.!..D6,6!G37,!_248.!,)!))!!<!?!,640!,2!.,2.)T0!2T" +
        "2<!!.7!702,:!!!)!;!!6.B.!:).!;,),!B00!6!,F342)!7!G!7,:.8))=!DD!7,)!,!<>.!!!23AGO!!C9!4!709!!!!!!)!>4" +
        "!<7:!!!.!B!:!),J,!!>!!!8!A,!2!!!)!))!!!!!=!"

    /** 전부 대문자로 쓰일 로그확률(공백 = 대문자 출현 없음). */
    const val LEX_UPPER =
        "0F?6<0600F=06060600?0<B< 60=E N<0  6 696090U6G 9<K0 0<900O0H<6 @06<0600000 099069000L090?@?A6H<09009" +
        "99Y0000  0= 06?<00 666M66@06  6 6C<6N0 EAH0 L000EG06<60D900 G 6006=ERCW0@0P?90G=9Y0S<6 0 060<6c06C0B" +
        "B6G6<J0006?6Nl@06@9<00<06H<`00K<0 S0N00 ZE0900600L0?06<6U 0X0060D000000 E606A 66@0F0 009B0U0D609IA0 " +
        "00P00D9<<00P0 60G?00?6M6N 000 6 000KM9B066e0J00ES9@ 06 0<0 A060 00 H000600J0090009TUg00 6C 06G06D0TJ" +
        "E0 0066<060< 0  6T006A60E06D<U09 BjAC060=00A669OE00<0<<PC =0N@E 0j?AA 096 6600I0F09C9=090 < ?0 a=00A" +
        "9F00<0 D<0 D 006 H0 C<O<0G6<0H969 O==0C09R?9 6000A0A9G<0  K0QO0E96090IK<000?@OTY00KB0A @=F000 0060KA" +
        "0C@?000< G0G069W9000000A0K90? 00 06600  0J0"

    const val LEX_LO = -15.236649484316096
    const val LEX_HI = -4.250763716974887

    /** 대문자 약어 글자 bigram(27기호: a~z + 경계) 로그확률, [이전*27 + 다음]. */
    const val ACR_BIGRAM =
        "`ckg[bc[gOaljpXfRoll_aZW_Wvreh_o_[XmU]id`n`PlicjYZW[Nuo_i`l^^mjRfec_qcNfggbZWSRNwobfdqb`^nUXcd_lbOgj" +
        "ac[YY[Oyj_hie_^VbPWkfn]cOroiZa^cZSwo]fclm_XpSXhb_keHjhgcXXXSNwobc^o]^hjKUheekeGjhegXXSWOyt]cdq[[UpD^" +
        "dc^oaQffgeWZP]NujanhhcfVbR[jhshePhnkWcU[QWstegemc^]l_b^ecpeAagfm]WOUStocdao[^apP`dddobIdkfdZ[K`Pwr_b" +
        "fr^]QpMYna[lbR[ged]VUbNvsheco__YnOX`f[mkGbkbcZVWXPwnZhko_o[lO_]Zdg^I^km_[TR^Xwaahd[``]aPbjjshgLplhic" +
        "dYXStp^gbn^`dlM[jb]lhQkkfeYUUVKwkaecdeU[gQXge`_eaggau[_]GUzq^ffq]bToO^`cdnaNbkjdYVQbLvm_ibk^ZgkL``c_" +
        "gfN_jod[[SXKzp^e^p[YhoLVb`[n`IkjgcbZU_Twgefff`e[eRbjjqXfKprkX^S[UXto^ecu^]UtOTecajbOgfc]]PUVOwsdhco`" +
        "adpSZ_bhmdRdkd_YcQYOtdbe]g_aYlPWdb^bhHbch[cPm`M}n_caiW_V^QZbcdla=_ibeTXXYW~r^`bo[[enMY[ack_M`abhYWU]" +
        "dzlinihgggibhgkhdjWhoidceZYW!"

    const val ACR_LO = -10.695189643065287
    const val ACR_HI = -0.768048488733063

}
