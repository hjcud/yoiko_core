# 개인 서버용 코어 모드

> 개인 코블몬 서버의 성장, 경제, 보상, 수집과 이벤트를 하나의 플레이 흐름으로 연결하는 NeoForge 1.21.1 기반 모드입니다.

이 저장소는 실행 가능한 배포본이 아니라, 직접 작성한 Java 코드와 주요 구현 내용을 정리해 공개하는 소스 저장소입니다. 게임 리소스와 외부 모드는 포함하지 않으므로 이 저장소만으로 완성된 모드를 실행할 수 없습니다.

![일일 보상과 연속 출석 화면](readme-assets/daily-reward.png)

## 서버 플레이를 하나의 메뉴로

서버 안에서 흩어지기 쉬운 프로필, 보관함, 우편, 거래, 수집과 보상 기능을 하나의 인터페이스로 묶었습니다. 플레이어는 같은 화면 흐름 안에서 자신의 진행 상황을 확인하고, 획득한 보상을 정리하며, 서버 콘텐츠에 참여할 수 있습니다.

<table>
  <tr>
    <td width="50%"><img src="readme-assets/storage-and-profile.png" alt="보관함과 플레이어 프로필 화면"></td>
    <td width="50%"><img src="readme-assets/relic-workbench.png" alt="유물 보관함과 강화 화면"></td>
  </tr>
  <tr>
    <td align="center">보관함과 프로필</td>
    <td align="center">유물 관리와 강화</td>
  </tr>
</table>

## 주요 시스템

### 성장과 보상

연속 접속 보상, 첫 접속 보상, 등급과 공개 프로필을 관리합니다. 포켓몬·유물·꾸미기 가챠와 도감도 같은 진행 체계 안에서 동작합니다.

### 경제와 거래

서버 화폐, 플레이어 간 거래소, 우편함과 개인 보관함을 제공합니다. 거래 및 운영 기록을 별도로 저장해 관리자가 경제 흐름과 이상 거래를 확인할 수 있도록 구성했습니다.

### 유물과 꾸미기

유물 획득, 감정, 강화, 분해, 장착과 효과 적용을 처리합니다. 장비형 꾸미기와 파티클 효과도 플레이어별 획득 및 장착 상태에 맞춰 동기화합니다.

### 서버 이벤트

일일 서버 이벤트, 낚시 축제, 속보 이벤트와 보물 토끼 탐색을 운영합니다. 일부 이벤트는 Xaero 월드맵과 연동해 탐색 구역을 표시할 수 있습니다.

### 거북이 경주

거북이 육성, 훈련, 전략 선택, 경기 참가, 베팅과 타임 트라이얼을 하나의 콘텐츠로 구현했습니다. 경기 상태와 기록은 서버에 저장되며 전용 화면과 HUD로 전달됩니다.

## 구현 구성

| 영역 | 주요 코드 | 역할 |
| --- | --- | --- |
| 메뉴와 화면 | [`menu`](src/main/java/com/yoiko/core/menu), [`client/screen`](src/main/java/com/yoiko/core/client/screen) | 서버 메뉴 흐름, 공통 위젯, 프로필·보관함·우편·거래소 화면 |
| 성장과 보상 | [`reward`](src/main/java/com/yoiko/core/reward), [`rank`](src/main/java/com/yoiko/core/rank), [`gacha`](src/main/java/com/yoiko/core/gacha) | 접속 보상, 등급, 포켓몬 가챠와 도감 진행 |
| 경제와 거래 | [`economy`](src/main/java/com/yoiko/core/economy), [`mail`](src/main/java/com/yoiko/core/mail), [`storage`](src/main/java/com/yoiko/core/storage) | 화폐, 거래소, 우편과 개인 보관함 |
| 유물과 꾸미기 | [`relic`](src/main/java/com/yoiko/core/relic), [`cosmetic`](src/main/java/com/yoiko/core/cosmetic) | 유물 생애 주기와 효과, 장비형·파티클 꾸미기 |
| 이벤트 | [`event`](src/main/java/com/yoiko/core/event), [`treasure`](src/main/java/com/yoiko/core/treasure), [`newspaper`](src/main/java/com/yoiko/core/newspaper) | 일일 이벤트, 속보, 보물 토끼와 주간 신문 |
| 거북이 경주 | [`turtle`](src/main/java/com/yoiko/core/turtle) | 육성, 훈련, 전략, 경기 시뮬레이션과 타임 트라이얼 |
| 저장과 통신 | [`data`](src/main/java/com/yoiko/core/data), [`network`](src/main/java/com/yoiko/core/network) | 플레이어·서버 상태 저장, 감사 기록과 클라이언트 동기화 |

## 개발 환경과 연동 모드

### 개발 환경

- Minecraft `1.21.1`
- NeoForge `21.1.234`
- Java `21`
- Cobblemon `1.7.3` 이상

### 선택 연동

| 모드 ID | 연동 기능 |
| --- | --- |
| `critical_strike` | 치명타 확률과 피해 관련 유물 효과 |
| `waystones` | 귀환 부적 유물 효과 |
| `mega_showdown` | 메가진화 관련 유물 효과 |
| `quality_food` | 음식 품질 관련 유물 효과 |
| `xaeroworldmap` | 속보 및 탐색 구역의 월드맵 표시 |

선택 연동 모드는 이 저장소에 포함하지 않으며, 각 모드의 저작권과 이용 조건은 원 제작자와 배포처의 정책을 따릅니다.

## 저장소 안내

이 저장소에는 다음 항목만 공개합니다.

- `src/main/java` 아래의 직접 작성한 Java 소스 코드
- 의존성과 프로젝트 구조를 설명하는 Gradle 설정
- NeoForge 모드 메타데이터 템플릿
- 저장소 소개를 위해 선별한 스크린샷

다음 항목은 공개하지 않습니다.

- 언어 파일, 모델, 애니메이션, 지오메트리와 데이터팩을 포함한 게임 리소스
- 코블몬을 비롯한 외부 모드와 외부 모드에서 추출한 자료
- 도트 이미지 원본, 편집 파일과 제작 중간 산출물
- 개발용 참고 자료, 설계 문서, 로그, 백업과 운영 데이터
- JAR 및 컴파일된 클래스와 같은 바이너리 파일

따라서 공개 저장소의 내용만으로 실제 서버에서 사용하는 완성된 모드를 재현하거나 배포할 수 없습니다.

## 저작권과 이용 조건

직접 작성한 프로젝트 코드와 문서는 별도 표시가 없는 한 모든 권리를 보유합니다. `TEMPLATE_LICENSE.txt`는 NeoForge MDK에서 제공된 템플릿 파일에만 적용됩니다.

> [!IMPORTANT]
> README 스크린샷에 포함된 UI 도트 그래픽, 아이콘과 그 밖의 직접 제작 시각물은 무단 복제를 허용하지 않습니다. 사전 서면 허가 없이 그 전체 또는 일부를 추출·복제·수정·재배포하거나 다른 프로젝트에 사용하는 것을 금지합니다. 저장소 공개는 이러한 시각물에 대한 이용 허락을 의미하지 않습니다.

README의 스크린샷은 프로젝트 소개를 위한 기록입니다. 화면에 포함된 Minecraft, Cobblemon 및 다른 외부 구성요소의 권리는 각 권리자에게 귀속됩니다.
