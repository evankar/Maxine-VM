pipeline {
    agent {
        dockerfile {
            filename 'Dockerfile'
            dir 'docker'
            args '--mount src="$HOME/.mx",target="/.mx",type=bind'
        }
    }
    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }
    environment {
        MAXINE_HOME="$WORKSPACE/maxine"
        MX_HOME="$WORKSPACE/mx"
        MX="$MX_HOME/mx"
        MX_GIT_CACHE="refcache"
    }

    stages {
        stage('clone') {
            steps {
                parallel 'maxine': {
                    dir(env.MAXINE_HOME) {
                        checkout scm
                    }
                }, 'mx': {
                    dir(env.MX_HOME) {
                        checkout([$class: 'GitSCM', branches: [[name: '5.190.3']], extensions: [[$class: 'CloneOption', shallow: true]], userRemoteConfigs: [[url: 'https://github.com/beehive-lab/mx.git']]])
                    }
                }
            }
        }
        stage('fetch dependencies') {
            steps {
                // Trigger fetch of dependencies
                dir(env.MAXINE_HOME) {
                    sh '$MX help'
                }
            }
        }
        stage('checkstyle-n-build') {
            steps {
                parallel 'checkstyle': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX --suite maxine checkstyle'
                    }
                }, 'build': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX build'
                    }
                }
            }
        }
        stage('image-n-test-init') {
            steps {
                parallel 'image': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX image @c1xgraal'
                        sh '$MX image -build=DEBUG -platform linux-aarch64 -isa Aarch64'
                        sh '$MX image -build=DEBUG -platform linux-arm -isa ARMV7'
                        sh '$MX -J-ea image'
                    }
                }, 'test-init': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX jttgen'
                        sh '$MX --suite maxine canonicalizeprojects'
                    }
                }
            }
        }
        stage('gate-n-crossisa') {
            steps {
                parallel 'gate': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX gate'
                    }
                }, 'crossisa': {
                    dir(env.MAXINE_HOME) {
                        sh '$MX --J @"-Dmax.platform=linux-aarch64 -Dtest.crossisa.qemu=1 -ea" testme -s=t -junit-test-timeout=1800 -tests=junit:aarch64.asm+Aarch64T1XTest+Aarch64T1XpTest+Aarch64JTT'
                        sh '$MX --J @"-Dmax.platform=linux-arm -Dtest.crossisa.qemu=1 -ea" testme -s=t -junit-test-timeout=1800 -tests=junit:armv7.asm+ARMV7T1XTest+ARMV7JTT'
                        sh '$MX --J @"-Dmax.platform=linux-riscv64 -Dtest.crossisa.qemu=1 -ea" testme -s=t -tests=junit:riscv64.asm+max.asm.target.riscv+riscv64.t1x'
                    }
                }
            }
        }
    }

    post {
        success {
            slackSend color: '#00CC00', message: "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
        failure {
            slackSend color: '#CC0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
    }
}
