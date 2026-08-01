// ExpenseWise CI pipeline (Jenkins, declarative syntax).
//
// Triggered by SCM polling on the main branch (configured in the Jenkins job,
// not here). Runs on the built-in Jenkins node, which already has JDK 21 and
// reaches SonarQube and the CI database over the shared "devops-net" network.
pipeline {
    agent any

    // These names must match the Global Tool names in Manage Jenkins -> Tools.
    // If your NodeJS tool is named something other than 'NodeJS', change it here.
    tools {
        jdk 'JDK21'
        maven 'Maven'
        nodejs 'NodeJS'
    }

    environment {
        // The backend's tests read these env vars (see application-local.yml and
        // application.yml). We point them at the dedicated ci-postgres container
        // on devops-net, NOT the dev database — reachable by container name.
        // All names carry the _EXPENSEWISE suffix to match the app's env vars,
        // which were renamed to avoid colliding with other projects' identically
        // named vars on a shared dev machine — see DECISIONS.md.
        DB_URL_EXPENSEWISE      = 'jdbc:postgresql://ci-postgres:5432/expensewise'
        DB_USER_EXPENSEWISE     = 'dev'
        DB_PASSWORD_EXPENSEWISE = 'devpass'
        // jwt.secret has no default, so the Spring context won't start without
        // it. A fixed dummy signs test tokens only; it is not a real secret.
        JWT_SECRET_EXPENSEWISE  = 'ci-test-secret-not-used-in-production-0123456789abcdef'
        // groq.api-key/model have no defaults either, for the same reason —
        // AiChatClient is @MockBean'd in every test, so this key is never
        // actually used to call Groq; it only lets the Spring context start.
        GROQ_API_KEY_EXPENSEWISE = 'ci-dummy-groq-key-never-actually-called'
        GROQ_MODEL_EXPENSEWISE   = 'llama-3.3-70b-versatile'
    }

    options {
        // Keep the last 15 builds only, and fail a build that hangs past 20 min.
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 20, unit: 'MINUTES')
    }

    stages {

        // 1. Pull the exact commit that triggered this run.
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // 2. Compile the backend and run unit + integration tests. The
        //    integration tests hit ci-postgres; Flyway builds the schema first.
        //    `junit` publishes the test results so Jenkins shows pass/fail trends.
        stage('Backend - Build & Test') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        // 3. Install locked dependencies and build the frontend. `npm run build`
        //    is `vue-tsc -b && vite build`, so a type error fails the pipeline.
        stage('Frontend - Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        // 4. Send the backend to SonarQube for quality analysis. withSonarQubeEnv
        //    injects the server URL and auth token from the Jenkins SonarQube
        //    server config, so no secrets appear here. Reuses the compiled
        //    classes from stage 2 (no `clean`).
        //    NOTE: 'SonarQube' must match the server Name in Manage Jenkins ->
        //    System -> SonarQube servers. Change it if yours differs.
        stage('SonarQube Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                              -Dsonar.projectKey=ExpenseWise \
                              -Dsonar.projectName=ExpenseWise
                        '''
                    }
                }
            }
        }

        // 5. Wait for SonarQube's verdict. SonarQube notifies Jenkins via a
        //    webhook when analysis finishes; this step then reads the result.
        //    abortPipeline:false = report the gate but don't fail the build yet
        //    (flip to true once coverage/JaCoCo is set up and the gate is tuned).
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        // 6. Keep the built backend JAR as a downloadable build artifact.
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true, allowEmptyArchive: true
            }
        }
    }

    post {
        success { echo 'CI passed: backend tested, frontend built, Sonar analysed.' }
        failure { echo 'CI failed — check the stage logs above.' }
    }
}
