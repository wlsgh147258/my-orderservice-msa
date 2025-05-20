
// 젠킨스 파일의 선언형 파이프라인 정의부 시작 (그루비 언어)
pipeline {
    agent any // 젠킨스 서버가 여러개 일때, 어느 젠킨스 서버에서나 실행이 가능
    environment{
        SERVICE_DIRS="config-service,discovery-service,gateway-service,ordering-service,user-service,product-service"
    }
    stages {
    // 각 작업 단위별로 stage로 나누어서 작성이 가능함. ()에 제목을 붙일 수 있음.
        stage('Pull Codes from Github') {
            steps {
                checkout scm // 젠킨스와 연결된 소스 컨트롤 매니저, (git 등)에서 코드를 가져오는 명령어
            }
        }
        stage('Detect Changes') {
            steps {
                script {
                    // 변경된 파일을 감지해보자! git 명령어를 통해서!
                    // returnStdout: true -> 결과를 출력하지 말고, 변수에 문자열로 넣어달라
                    def changedFiles = sh(script:"git diff --name-only HEAD~1 HEAD", returnStdout: true)
                                        .trim()
                                        .split("\n") // 변경된 파일을 줄 단위로 분리
                    // 변경된 파일 출력
                    // [user-service/src/main/resources/application.yml,
                    // user-service/src/main/java/com/playdata/userservice/controller/UserController.java,
                    // ordering-service/src/main/resources/application.yml]
                    echo "ChangedFiles: ${changedFiles}"

                    def changedServices = []
                    def serviceDirs = env.SERVICE_DIRS.split(",")

                    serviceDirs.each{ service ->
                        // changedDiles 라는 리스트를 조회해서 service 변수에 들어온 서비스 이름과
                        // 하나라도 일치하는 이름이 있다면 true, 하나도 존재하지 않으면 false
                        // service: user-service
                        // service: user-service -> 변경된 파일 경로가 user-service/로 시작한다면 true

                        if (changedFiles.any { it.startsWith(service + "/") }) {
                            changedServices.add(service)
                        }
                    }

                    // 변경된 서비스 이름을 모아놓은 리스트를 다른 스테이지에서도 사용하기 위해 환경 변수로 선언.
                    // join() -> 지정한 문자열을 구분자로 하여 리스트 요소를 하나의 문자열로 리턴. 중복
                    // 환경 변수는 문자열만 선언할 수 있어서 join을 사용함.
                    env.CHANGED_SERVICE = changedServices.join(",")
                    if(env.CHANGED_SERVICE == " "){
                        echo " NO changes detected in service directories. Skipping build and deployment."
                        // 성공 상태로 파이프라인을 종료
                        currentBuild.result = 'SUCCESS'

                    }



                }
            }
        }
        stage('Build Changed Services') {
        // 이 스테이지는 빌드 되어야 할 서비스가 존재한다면 실행되는 스테이지.
        // 이전 스테이지에서 세팅한 CHANGED_SERVICE라는 환경변수가 비어있지 않아야만 실행.

            when {
                expression { env.CHANGED_SERVICE != ""}
            }
            steps {
                script {
                // 환경 변수 불러오기
                    def changedServices = env.CHANGED_SERVICE.split(",")
                    changedServices.each { service ->
                        sh """
                         echo "Building ${service}"
                         cd ${service}
                         chmod +x gradlew
                         ./gradlew clean build -x test
                         ls -al ./build/libs
                         cd ..
                        """
                    }
                }
            }
        }
    }
}