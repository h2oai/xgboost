@Library('test-shared-library@1.9') _

import ai.h2o.ci.BuildResult
import ai.h2o.ci.buildsummary.DetailsSummary
import ai.h2o.ci.buildsummary.StagesSummary

buildSummary('https://github.com/h2oai/xgboost', true)
buildSummary.get().addStagesSummary(this, new StagesSummary())
BuildResult result = BuildResult.FAILURE

TARGET_NEXUS_NONE = 'none'
TARGET_NEXUS_LOCAL = 'local'
TARGET_NEXUS_SNAPSHOT = 'snapshot'
TARGET_NEXUS_PUBLIC = 'public'
properties([
    parameters([
        choice(name: 'targetNexus', choices: [TARGET_NEXUS_NONE, TARGET_NEXUS_LOCAL, TARGET_NEXUS_PUBLIC], description: 'Nexus to upload artifacts to.')
    ])
])

DEFAULT_NODE_LABEL = 'docker && !mr-0xc8'
PUBLISHABLE_BRANCH_NAME = 'h2o3'
ARCHIVED_FILES = '**/ci-build/*.jar, **/ci-build/*.whl, **/ci-build/*.log, **/jvm-packages/xgboost4j/*.jar, **/jvm-packages/xgboost4j/*.log'

XGB_MAJOR_VERSION = '0.7'
XGB_VERSION = "${XGB_MAJOR_VERSION}.${currentBuild.number}"

def targetNexus = params.targetNexus ?: TARGET_NEXUS_NONE
targetNexus = targetNexus.toLowerCase()
if (env.BRANCH_NAME != PUBLISHABLE_BRANCH_NAME) {
    XGB_VERSION = "0.8.${currentBuild.number}-${env.BRANCH_NAME.replaceAll('/|\\ ', '-').toLowerCase()}-SNAPSHOT"
    if (targetNexus != TARGET_NEXUS_NONE) {
        targetNexus = TARGET_NEXUS_SNAPSHOT
    }
}

MAKE_OPTS = "CI=1 XGB_VERSION=${XGB_VERSION} TARGET_NEXUS=${targetNexus} PY_VERSION=27"

CONFIGURATIONS = [
    [backend: 'minimal', os: 'osx', node: 'osx'],

    [backend: 'minimal', os: 'linux', hasImage: true],
    [backend: 'ompv4', os: 'linux', hasImage: true],
    [backend: 'gpuv4', os: 'linux', hasImage: true],

    [backend: 'ompv3', os: 'linux', hasImage: true],
    [backend: 'gpuv3', os: 'linux', hasImage: true],
]

Map CHECK_IMAGES = [
    linux_minimal: [
        [ name: 'Check MINIMAL with CentOS 6.5', os: 'centos6.5', hasImage: true ],
        [ name: 'Check MINIMAL with CentOS 6.8', os: 'centos6.8', hasImage: true ],
        [ name: 'Check MINIMAL with CentOS 7.3', os: 'centos7.3', hasImage: true ],
        [ name: 'Check MINIMAL with Ubuntu 14', os: 'ubuntu14', hasImage: true ],
        [ name: 'Check MINIMAL with Ubuntu 16', os: 'ubuntu16', hasImage: true ]
    ],
    osx_minimal: [
        [name: 'Check MINIMAL with OS X', node: 'osx', os: 'osx'],
    ],
    linux_ompv3: [
        [name: 'Check OMP with Ubuntu 14', os: 'ubuntu14', hasImage: true],
    ],
    linux_ompv4: [
        [name: 'Check OMP with CentOS 6.5', os: 'centos6.5', hasImage: true],
        [name: 'Check OMP with CentOS 6.8', os: 'centos6.8', hasImage: true],
        [name: 'Check OMP with CentOS 7.3', os: 'centos7.3', hasImage: true],
        [name: 'Check OMP with Ubuntu 16', os: 'ubuntu16', hasImage: true]
    ],
    linux_gpuv3: [
        [name: 'Check GPU with Ubuntu 14', os: 'ubuntu14', hasImage: true],
    ],
    linux_gpuv4: [
       [name: 'Check GPU with CentOS 6.9', os: 'centos6.9', hasImage: true],
       [name: 'Check GPU with CentOS 7.4', os: 'centos7.4', hasImage: true],
       [name: 'Check GPU with Ubuntu 16', os: 'ubuntu16', hasImage: true]
    ]
]

ansiColor('xterm') {
    timestamps {
        try {
            node(DEFAULT_NODE_LABEL) {
                sh 'env'

                buildSummary.stageWithSummary('Initialize') {
                    deleteDir()
                    def scmEnv = checkout scm

                    env.BRANCH_NAME = scmEnv['GIT_BRANCH'].replaceAll('origin/', '')

                    DetailsSummary detailsSummary = new DetailsSummary()
                    detailsSummary.setEntry(this, 'XGBoost Version', XGB_VERSION)
                    detailsSummary.setEntry(this, 'Target Nexus', targetNexus)
                    detailsSummary.setEntry(this, 'Make Opts', MAKE_OPTS)
                    buildSummary.get().addDetailsSummary(this, detailsSummary)

                    sh 'git submodule update --init --recursive'
                }

                buildSummary.stageWithSummary('Patch, Write Version and Stash') {
                    sh """
                        cd dmlc-core/
                        wget https://raw.githubusercontent.com/h2oai/xgboost/master/patches/01_dmlc_core_imports.patch
                        git apply 01_dmlc_core_imports.patch
                        cd ..
                        make ${MAKE_OPTS} -f ci/Makefile.jenkins centos_write_version_in_docker
                    """
                    stash 'xgboost-sources'
                }
            }

            parallel(CONFIGURATIONS.collectEntries { config ->
                [
                    stageNameFromConfig(config), {
                        config.stageDir = getStageDirFromConfiguration(config)

                        def nodeLabel = config.node
                        if (nodeLabel == null) {
                            nodeLabel = DEFAULT_NODE_LABEL
                        }
                        buildSummary.stageWithSummary(stageNameFromConfig(config), config.stageDir) {
                            node(nodeLabel) {
                                buildSummary.refreshStage(stageNameFromConfig(config))
                                timeout(time: 60, unit: 'MINUTES') {
                                    try {
                                        deleteDir()
                                        dir(config.stageDir) {
                                            unstash 'xgboost-sources'
                                            def inDockerSuffix = ''
                                            if (config.hasImage) {
                                                inDockerSuffix = '_in_docker'
                                            }
                                            sh "make ${MAKE_OPTS} -f ci/Makefile.jenkins build_${config.backend}${inDockerSuffix}"
                                            sh "make ${MAKE_OPTS} -f ci/Makefile.jenkins jar_${config.backend}${inDockerSuffix}"
                                            sh "make ${MAKE_OPTS} -f ci/Makefile.jenkins whls_${config.backend}${inDockerSuffix}"
                                        }

                                        CHECK_IMAGES["${config.os}_${config.backend}"].each {checkConfig ->
                                            buildSummary.stageWithSummary(checkConfig.name, config.stageDir) {
                                                dir (config.stageDir) {
                                                    def inDockerSuffix = ''
                                                    if (checkConfig.hasImage) {
                                                        inDockerSuffix = '_in_docker'
                                                    }
                                                    sh "make ${MAKE_OPTS} -f ci/Makefile.jenkins ${checkConfig.os}_check_${config.backend}${inDockerSuffix}"
                                                }
                                            }
                                        }

                                        stash name: "xgboost-${config.os}-${config.backend}-artifacts", includes: '**/ci-build/*.jar, **/ci-build/*.whl'
                                    } finally {
                                        archiveArtifacts artifacts: ARCHIVED_FILES, allowEmptyArchive: true
                                    }
                                }
                            }
                        }
                    }
                ]
            })

            if (targetNexus != 'none') {
                node('master') {
                    buildSummary.stageWithSummary('Nexus Deploy') {
                        def awscliDockerImage = 'docker.h2o.ai/awscli'
                        sh "docker pull ${awscliDockerImage}"

                        dir ('build-lib') {
                            deleteDir()
                            unstash 'xgboost-sources'
                            withCredentials([file(credentialsId: 'nexus-settings-xml', variable: 'MAVEN_SETTINGS_PATH'), file(credentialsId: 'release-secret-key-ring-file', variable: 'SECRING_PATH')]) {
                                sh "make ${MAKE_OPTS} -f ci/Makefile.jenkins deploy_lib_jar_in_docker"
                            }
                            archiveArtifacts artifacts: "jvm-packages/xgboost4j/target/xgboost4j-${XGB_VERSION}.jar", allowEmptyArchive: false
                            if (targetNexus == TARGET_NEXUS_PUBLIC) {
                                s3Upload("jvm-packages/xgboost4j/target", "xgboost4j-${XGB_VERSION}.jar")
                            }
                        }
                        CONFIGURATIONS.each { config ->
                            buildSummary.stageWithSummary("Deploy ${config.os}-${config.backend}") {
                                config.stageDir = getStageDirFromConfiguration(config)

                                deleteDir()
                                unstash name: "xgboost-${config.os}-${config.backend}-artifacts"

                                dir(getStageDirFromConfiguration(config)) {
                                    unstash 'xgboost-sources'
                                    withCredentials([file(credentialsId: 'nexus-settings-xml', variable: 'MAVEN_SETTINGS_PATH'), file(credentialsId: 'release-secret-key-ring-file', variable: 'SECRING_PATH')]) {
                                        sh "make ${MAKE_OPTS} TARGET_OS=${config.os} -f ci/Makefile.jenkins deploy_jar_${config.backend}_in_docker"
                                    }
                                    if (targetNexus == TARGET_NEXUS_PUBLIC) {
                                        s3Upload('ci-build', '*.jar')
                                        s3Upload('ci-build', '*.whl')
                                    }
                                }
                            }
                        }
                        if (targetNexus == TARGET_NEXUS_PUBLIC) {
                            docker.withRegistry("https://docker.h2o.ai") {
                                docker.image('docker.h2o.ai/opsh2oai/hub').inside("--init -v /home/jenkins/.ssh:/home/jenkins/.ssh:ro -v /home/jenkins/.gitconfig:/home/jenkins/.gitconfig:ro") {
                                    buildSummary.stageWithSummary("GitHub Release") {
                                        withCredentials([string(credentialsId: 'h2o-ops-personal-auth-token', variable: 'GITHUB_TOKEN')]) {
                                            checkout scm
                                            sh "hub release create h2o3-v${XGB_VERSION} -m \"h2o3_v${XGB_VERSION}\""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            result = BuildResult.SUCCESS
        } finally {
            sendEmailNotif(result, buildSummary.get().toEmail(this), ['michalr@h2o.ai'])
        }
    }
}

private String stageNameFromConfig(final config) {
    return "Build ${config.backend.toUpperCase()} with ${config.os}"
}

private GString getStageDirFromConfiguration(final config) {
    return "${config.os}-${config.backend}"
}

private void s3Upload(final String folder, final String file) {
    docker.withRegistry("https://docker.h2o.ai") {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AWS S3 Credentials', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            docker.image('docker.h2o.ai/awscli').inside("--init -e AWS_ACCESS_KEY_ID=\${AWS_ACCESS_KEY_ID} -e AWS_SECRET_ACCESS_KEY=\${AWS_SECRET_ACCESS_KEY}") {
                sh "aws s3 sync ${folder} s3://h2o-release/xgboost/${env.BRANCH_NAME}/${XGB_VERSION} --exclude '*' --include '${file}'"
            }
        }
    }
}
