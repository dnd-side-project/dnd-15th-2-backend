# miri

Miri 백엔드 프로젝트의 초기 실행 환경입니다. 도메인 또는 계층별 패키지 구조는 아직 추가하지 않았습니다.

## 기술 구성

- Spring Boot 3.5.16
- Java 21
- Gradle 8.14.3 Wrapper
- JUnit 5
- Docker / Docker Compose

## Docker로 실행

Windows에서 Docker Desktop을 실행한 뒤 다음 명령을 사용합니다.

```powershell
docker compose up --build
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다. 종료할 때는 다음 명령을 사용합니다.

```powershell
docker compose down
```

포트를 바꾸려면 `APP_PORT` 환경 변수를 지정합니다.

```powershell
$env:APP_PORT = "8081"
docker compose up --build
```

## 테스트

Docker 이미지의 `build` 단계에서 JUnit 5 테스트가 자동으로 실행됩니다.

```powershell
docker build --target build -t miri:test .
```

호스트에 JDK 21이 설치되어 있다면 Gradle Wrapper로 직접 실행할 수도 있습니다.

```powershell
.\gradlew.bat test
```

## GitHub SSH 등록

이 저장소용 GitHub SSH 키는 프로젝트 외부의 다음 위치에 생성됩니다.

```text
C:\Users\SSAFY\Documents\Codex\.ssh\id_ed25519_github
```

공개 키를 클립보드에 복사한 뒤 GitHub의 **Settings → SSH and GPG keys → New SSH key**에 등록합니다.

```powershell
Get-Content C:\Users\SSAFY\Documents\Codex\.ssh\id_ed25519_github.pub | Set-Clipboard
```

키를 등록한 뒤 이 저장소에만 GitHub 작성자와 SSH 키를 지정합니다.

```powershell
git config --local user.name "Kim Do Yeon"
git config --local user.email "tkv00@naver.com"
git config --local core.sshCommand "ssh -i C:/Users/SSAFY/Documents/Codex/.ssh/id_ed25519_github -o IdentitiesOnly=yes -o UserKnownHostsFile=C:/Users/SSAFY/Documents/Codex/.ssh/known_hosts_github"
```

전역 GitLab 설정은 유지되므로 두 계정을 저장소별로 병행할 수 있습니다.
