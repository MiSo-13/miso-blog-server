pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        COMPOSE_FILE_PATH = 'docker-compose.deploy.yml'
        DEPLOY_ENV_FILE = '.env.deploy'
    }

    stages {
        stage('Build') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'sh ./gradlew clean build --console plain'
                    } else {
                        bat 'gradlew.bat clean build --console plain'
                    }
                }
            }
        }

        stage('Write Deploy Env') {
            steps {
                script {
                    String deployEnv = """
MISO_BLOG_PORT=${env.MISO_BLOG_PORT ?: '8010'}
MISO_BLOG_NETWORK_NAME=${env.MISO_BLOG_NETWORK_NAME ?: 'miso-blog-network'}
DB_URL=${env.DB_URL ?: ''}
DB_USERNAME=${env.DB_USERNAME ?: ''}
DB_PASSWORD=${env.DB_PASSWORD ?: ''}
RABBITMQ_USERNAME=${env.RABBITMQ_USERNAME ?: 'guest'}
RABBITMQ_PASSWORD=${env.RABBITMQ_PASSWORD ?: 'guest'}
RABBITMQ_PORT=${env.RABBITMQ_PORT ?: '5673'}
RABBITMQ_MANAGEMENT_PORT=${env.RABBITMQ_MANAGEMENT_PORT ?: '15673'}
OPENAI_API_KEY=${env.OPENAI_API_KEY ?: ''}
OPENAI_ADMIN_KEY=${env.OPENAI_ADMIN_KEY ?: ''}
OPENAI_MODEL=${env.OPENAI_MODEL ?: 'gpt-4.1-mini'}
OPENAI_BUDGET_LIMIT_USD=${env.OPENAI_BUDGET_LIMIT_USD ?: ''}
GITHUB_TOKEN=${env.GITHUB_TOKEN ?: ''}
GITHUB_OWNER=${env.GITHUB_OWNER ?: ''}
BLOG_PUBLIC_BASE_URL=${env.BLOG_PUBLIC_BASE_URL ?: 'http://localhost:8010'}
BLOG_MEDIA_UPLOAD_DIR=${env.BLOG_MEDIA_UPLOAD_DIR ?: '/data/miso-blog/uploads'}
BLOG_MEDIA_PUBLIC_URL_PREFIX=${env.BLOG_MEDIA_PUBLIC_URL_PREFIX ?: '/media'}
BLOG_MEDIA_MAX_FILE_SIZE=${env.BLOG_MEDIA_MAX_FILE_SIZE ?: '10MB'}
BLOG_MEDIA_MAX_REQUEST_SIZE=${env.BLOG_MEDIA_MAX_REQUEST_SIZE ?: '30MB'}
BLOG_MEDIA_MAX_FILE_SIZE_BYTES=${env.BLOG_MEDIA_MAX_FILE_SIZE_BYTES ?: '10485760'}
MISO_BLOG_UPLOAD_ROOT=${env.MISO_BLOG_UPLOAD_ROOT ?: '/srv/miso-blog/uploads'}
MISO_BLOG_LOCAL_PROJECTS_ROOT=${env.MISO_BLOG_LOCAL_PROJECTS_ROOT ?: '/srv/projects'}
MISO_BLOG_JAVA_OPTS=${env.MISO_BLOG_JAVA_OPTS ?: '-Xms256m -Xmx512m'}
""".trim()

                    writeFile file: env.DEPLOY_ENV_FILE, text: deployEnv
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    String composeBaseCommand = "docker compose --env-file ${env.DEPLOY_ENV_FILE} -f ${env.COMPOSE_FILE_PATH}"

                    if (isUnix()) {
                        sh """
${composeBaseCommand} up -d miso-blog-rabbitmq
${composeBaseCommand} up -d --build --remove-orphans miso-blog-server
"""
                    } else {
                        bat """
${composeBaseCommand} up -d miso-blog-rabbitmq
${composeBaseCommand} up -d --build --remove-orphans miso-blog-server
"""
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (isUnix()) {
                    sh "rm -f ${env.DEPLOY_ENV_FILE}"
                } else {
                    bat "if exist ${env.DEPLOY_ENV_FILE} del /f /q ${env.DEPLOY_ENV_FILE}"
                }
            }
        }
    }
}
