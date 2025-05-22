// 자주 사용되는 필요한 변수는 전역으로 선언하는 것도 가능
// ECR credential helper 이름
def ecrLoginHelper = "docker-credential-ecr-login"
def deployHost = "172.31.33.188" // 배포 인스턴스의 프라이빗 주소


// 젠킨스 파일의 선언형 파이프라인 정의부 시작 (그루비 언어)
pipeline {
    agent any // 젠킨스 서버가 여러개 일때, 어느 젠킨스 서버에서나 실행이 가능
    environment{
        SERVICE_DIRS="config-service,discovery-service,gateway-service,ordering-service,user-service,product-service"
        ECR_URL="597088024931.dkr.ecr.ap-northeast-2.amazonaws.com"
        REGION="ap-northeast-2"
    }
    stages {
    // 각 작업 단위별로 stage로 나누어서 작성이 가능함. ()에 제목을 붙일 수 있음.
        stage('Pull Codes from Github') {
            steps {
                checkout scm // 젠킨스와 연결된 소스 컨트롤 매니저, (git 등)에서 코드를 가져오는 명령어
            }
        }
        stage('Add Secret To Config-service'){
            steps {
                withCredentials([file(credentialsId: 'config-secret', variable: 'configSecret')]){
                    // 중복된 sh 명령 제거, 이 블록 하나만 있어도 충분
                    sh 'cp $configSecret config-service/src/main/resources/application-dev.yml'
                    // script{} 블록도 불필요하여 제거
                }
            }
        }
        stage('Detect Changes') {
            steps {
                script {
                    def commitCount = sh(script:"git rev-list --count HEAD", returnStdout: true).trim().toInteger()

                    def changedServices = []
                    def serviceDirs = env.SERVICE_DIRS.split(",")

                    if (commitCount == 1) {
                        echo "Initial commit Detected. All services will be built."
                        changedServices = serviceDirs
                    }
                    else {
                        def changedFiles = sh(script:"git diff --name-only HEAD~1 HEAD", returnStdout: true).trim().split("\n")
                        echo "ChangedFiles: ${changedFiles}"

                        // Docker Compose 파일 변경 시 모든 서비스를 다시 빌드하도록 변경
                        if (changedFiles.contains("docker-compose.yml")) {
                            echo "docker-compose.yml changed. All services will be built and deployed."
                            changedServices = serviceDirs
                        } else {
                            serviceDirs.each{ service ->
                                if (changedFiles.any {it.startsWith(service + "/")}) {
                                    changedServices.add(service)
                                }
                            }
                        }
                    }

                    env.CHANGED_SERVICES = changedServices.unique().join(",") // 중복 제거 및 환경 변수 설정
                    if(env.CHANGED_SERVICES == "") {
                        echo "No relevant changes Detected. Skipping Build/Deploy Stages"
                        currentBuild.result = 'SUCCESS'
                        return // 파이프라인 종료
                    }
                    echo "Services to be processed: ${env.CHANGED_SERVICES}" // 어떤 서비스가 처리될지 명확히 출력
                }
            }
        }
        stage('Build changed Services') {
            when {
                expression { env.CHANGED_SERVICES != "" } // 변경된 서비스가 있을 때만 실행
            }
            steps {
                script {
                    def changedServicesList = env.CHANGED_SERVICES.split(",")
                    changedServicesList.each { service ->
                        // 각 명령을 별도의 sh 블록으로 분리하여 Groovy 변수 보간 문제 해결
                        echo "Building ${service}"
                        sh "cd ${service}"
                        sh "chmod +x gradlew"
                        sh "./gradlew clean build -x test"
                        sh "ls -al ./build/libs"
                        sh "cd .." // 원본 디렉토리로 돌아오기
                    }
                }
            }
        }

         stage('Build Docker Image & Push to AWS ECR') {
            when {
                expression { env.CHANGED_SERVICES != "" } // 변경된 서비스가 있을 때만 실행
            }
            steps {
                script {
                    withAWS(region: "${REGION}", credentials:"aws-key"){
                        // ECR 인증 헬퍼 설정은 루프 밖에서 한 번만 실행
                        sh """
                        curl -O https://amazon-ecr-credential-helper-releases.s3.us-east-2.amazonaws.com/0.4.0/linux-amd64/${ecrLoginHelper}
                        chmod +x ${ecrLoginHelper}
                        mv ${ecrLoginHelper} /usr/local/bin/
                        mkdir -p ~/.docker
                        echo '{"credHelpers": {"${ECR_URL}": "ecr-login"}}' > ~/.docker/config.json
                        """

                        def changedServicesList = env.CHANGED_SERVICES.split(",")
                        changedServicesList.each { service ->
                           if (service != "") {
                             echo "Building and Pushing Docker image for: ${service}"
                             // 각 docker 명령도 별도의 sh 블록으로 분리
                             sh "docker build -t ${service}:latest ${service}"
                             sh "docker tag ${service}:latest ${ECR_URL}/${service}:latest"
                             sh "docker push ${ECR_URL}/${service}:latest"
                           }
                        }
                    }
                }
            }
         }
           stage('Deploy Changed Services to AWS EC2'){
                     when {
                         expression { env.CHANGED_SERVICES != "" }
                     }
                     steps{
                        sshagent(credentials: ["deploy-key"]){
                            // 각 명령을 별도의 sh 블록으로 분리
                            sh "ssh -o StrictHostKeyChecking=no ubuntu@${deployHost} 'mkdir -p /home/ubuntu/app'"

                            // Docker Compose 파일 복사 (대상 경로 변경: /home/ubuntu/app/docker-compose.yml)
                            sh "scp -o StrictHostKeyChecking=no docker-compose.yml ubuntu@${deployHost}:/home/ubuntu/app/docker-compose.yml"

                            // Docker Compose 실행. deployHost 변수 사용
                            // ssh 명령 내부에 set -ex 추가하여 디버깅 용이하게
                            sh "ssh -o StrictHostKeyChecking=no ubuntu@${deployHost} 'set -ex && cd /home/ubuntu/app && aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_URL} && docker-compose pull ${env.CHANGED_SERVICES.replace(",", " ")} && docker-compose up -d ${env.CHANGED_SERVICES.replace(",", " ")}'"
                            // docker-compose pull/up 명령은 여러 인자를 공백으로 구분하여 받으므로 join(",") 대신 replace(",", " ") 사용
                        }
                     }
                  }
        }
    }