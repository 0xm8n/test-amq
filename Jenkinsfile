#!groovy

def mvnCmd = "mvn --settings configuration/settings.xml -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"

pipeline {
    agent {
        label 'maven'
    }

    stages {

        stage ('Check Git Tag') {
            steps {
                script {
                    echo "Split tag version '${env.APP_NAME}':'${env.TAG}'"
                    def arrs = "${env.TAG}".tokenize( '.' )
                    env.mainV = arrs[0]
                    env.mainAppName = "${env.APP_NAME}-${env.mainV}"
                    env.newDeploymentApp = false
                    echo "Pipeline is working with '${env.mainAppName}' deployment config"
                }
            }
        }


        stage ('Build App by Git Tag') {
            steps {
                script {
                    echo "Process to Deploy API '${env.APP_NAME}':'${env.TAG}'_sit to '${env.OPENSHIFT_PROJECT}'"
                    sh " ${mvnCmd} -U clean install -DskipTests=true"
                }
            }
        }

        stage('Create Image Builder') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            return !openshift.selector("bc", env.APP_NAME).exists();
                        }
                    }
                }
            }
            steps {
                script {
                    echo "Create Build Config"
                    openshift.withCluster() {
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            echo "Create build from image stream '${env.IMAGE_STREAM_NAME}'"
                            openshift.newBuild("--name=${env.APP_NAME}", "--image-stream=${env.IMAGE_STREAM_NAME}" , "--binary=true")
                        }
                    }
                }
            }
        }

        stage('Build Image') {
            steps {
                sh "cp target/*.jar target/app.jar"
                script {
                    openshift.withCluster() {
                        echo "Start Build Image"
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            openshift.selector("bc", env.APP_NAME).startBuild("--from-file=target/app.jar", "--wait=true")
                        }
                    }
                }
            }
        }

        stage('Tag Image') {
            steps {
                script {
                    openshift.withCluster() {
                        echo "Start Tag Image"
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            openshift.tag("${env.APP_NAME}:latest", "${env.APP_NAME}:${env.TAG}_sit")
                        }
                    }
                }
            }
        }

        stage('Deploy to SIT') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            echo "${env.mainAppName}"
                            return !openshift.selector('deployment', env.mainAppName).exists()
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            echo "Start new App in SIT"
                            openshift.newApp("--image-stream=${env.APP_NAME}:${env.TAG}_sit", "--name=${env.mainAppName}" , "TZ=Asia/Bangkok", "LANG=en_US.UTF-8", "SPRING_PROFILES_ACTIVE=sit")
                            sleep 10
                            echo "Patch Container name"
                            openshift.patch("deployment/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/template/spec/containers/0/name\", \"value\":\"${env.mainAppName}\"}]'")
                            echo "Patch Deployment to have node selector"
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"spec\":{\"nodeSelector\":{\"apps\":\"fuse\"}}}}}'")
                            echo "Patch Deployment to use sidecar inject'"
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"sidecar.istio.io/inject\":\"true\"}}}}}'")
                            echo "Patch Deployment label for istio'"
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"app\":\"${env.mainAppName}\"}}}}}'")
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"version\":\"${env.mainV}\"}}}}}'")
                            echo "Patch Deployment to allow manual route'"
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"maistra.io/expose-route\":\"true\"}}}}}'")
                            echo "Patch Service port name"
                            openshift.patch("svc/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/ports/0/name\", \"value\":\"http-8080\"}]'")
                            openshift.patch("svc/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/ports/1/name\", \"value\":\"tcp-8443\"}]'")
                            openshift.patch("svc/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/ports/2/name\", \"value\":\"tcp-8778\"}]'")
                            openshift.patch("svc/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/ports/3/name\", \"value\":\"tcp-9779\"}]'")
                            echo "Add readiness probe and liveness probe"
                            openshift.set("probe", "deployment/${env.mainAppName} --readiness --get-url=http://:8080/actuator/health --initial-delay-seconds=30 --failure-threshold=3 --period-seconds=15 --timeout-seconds=5")
                            openshift.set("probe", "deployment/${env.mainAppName} --liveness  --get-url=http://:8080/actuator/health --initial-delay-seconds=150 --failure-threshold=3 --period-seconds=30 --timeout-seconds=5")
                            echo "Add environment variable for Spring config client from secret"
                            openshift.set('env', "deployment/${env.mainAppName}", '--from=secret/config-client', '--prefix=OCP_SPRING_CONFIG_')
                            echo "Add environment variable for AMQP client from secret"
                            openshift.set('env', "deployment/${env.mainAppName}", '--from=secret/apiamq-credentials-secret', '--prefix=OCP_SPRING_')
                            echo "Add volume for truststore from secret"
                            openshift.set('volume', "deployment/${env.mainAppName}", "--add", "--secret-name=test-amq-truststore", "--name=${env.mainAppName}-truststore-volume", "--mount-path=/truststore")
                            echo "Add label for Prometheus monitoring through JMX exporter"
                            openshift.selector("svc", env.mainAppName).label([ 'monitoring-exporter':'fuse-jmx' ], "--overwrite")
                            echo "Patch Deployment to pod anti-affinity"
                            openshift.patch("deployment/${env.mainAppName}", "'{\"spec\":{\"template\":{\"spec\":{\"affinity\":{\"podAntiAffinity\":{\"preferredDuringSchedulingIgnoredDuringExecution\":[{\"podAffinityTerm\":{\"labelSelector\":{\"matchExpressions\":[{\"key\":\"deployment\",\"operator\":\"In\",\"values\":[\"${env.mainAppName}\"]}]},\"topologyKey\":\"kubernetes.io/hostname\"},\"weight\":100}]}}}}}}'")
                            def d = openshift.selector("deployment",  env.mainAppName)
                            echo "Getting pod selector from labels"
                            def p = openshift.selector("pod",  [ app : "${env.mainAppName}" ])
                            echo "Pod name list = ${p.names()}"
                            echo "Total pod in pod selector is ${p.count()}"
                            echo "Awaiting pod readiness"
                            timeout(10) { // Timeout 10 minutes
                              def containerReadyStatus = false
                              while (p.count() > 1 || containerReadyStatus == false) { // Check until only number of pod is stable
                                echo "#### BEGIN POD STATUS CHECKING ####"
                                p.withEach {
                                  containerReadyStatus = false
                                  echo "Processing pod name = ${it.name()}"
                                  if (it.exists()) {  // First check to ensure pod is exist.
                                    sleep 5
                                    if (it.exists()) {  // Double check to ensure pod is exist.
                                      def podObj = it.object()
                                      def totalNumOfContainer = podObj.status.containerStatuses.size
                                      podObj.status.containerStatuses.eachWithIndex { entry, i ->
                                        echo "Checking status of container = ${entry.name} of pod = ${it.name()} and ready status = ${entry.ready}"
                                        if (entry.name == env.mainAppName) {
                                          containerReadyStatus = entry.ready
                                        }
                                      }
                                    }
                                  }
                                }
                                sleep 5
                                // echo "p.count() = ${p.count()}, containerReadyStatus = ${containerReadyStatus}"
                                echo "#### END POD STATUS CHECKING ####"
                              }
                            }
                            echo "All replica are ready"

                            echo "Set all triggers to manual for Deployment : '${env.mainAppName}'"
                            openshift.set("triggers", "deployment/${env.mainAppName} --manual")

                            if (openshift.selector('is', env.mainAppName).exists()) {
                                openshift.delete("is/${env.mainAppName}")
                                echo "Image stream '${env.mainAppName}' deleted"
                            }
                            env.newDeploymentApp = true
                        }
                    }
                }
            }
        }

        stage('Re Deploy to SIT') {
            when {
                expression {
                    echo "The new Deployment flag is ${env.newDeploymentApp}"
                    return !env.newDeploymentApp.toBoolean()
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject(env.OPENSHIFT_PROJECT) {
                            echo "Get image SHA256 of image stream tag '${env.APP_NAME}:${env.TAG}_sit'"
                            tags = sh(script: "oc get is ${env.APP_NAME} -n ${env.OPENSHIFT_PROJECT} -o jsonpath=\"{.spec.tags[?(@.name=='${env.TAG}_sit')].from.name}\"", returnStdout: true).trim()
                            echo "Patch Deployment to use newly tagged image --> '${env.APP_NAME}:${env.TAG}_sit'"
                            openshift.patch("deployment/${env.mainAppName} --type=json", "'[{\"op\": \"replace\", \"path\": \"/spec/template/spec/containers/0/image\", \"value\":\"${env.IMAGE_REGISTRY_BASE_URL}/${env.OPENSHIFT_PROJECT}/${tags}\"}]'")
                            echo "Start rolling out app on SIT"
                            def d = openshift.selector("deployment",  env.mainAppName)
                            def rm = d.rollout()
                            if(d.object().spec.paused == true){
                                d.rollout().resume()
                            }
                            d.describe()   // Describe Deployment
                            timeout(10) { // Throw exception after 10 minutes
                                rm.status()
                            }
                            echo "A pod is running properly now"
                            d.rollout().pause()
                        }
                    }
                }
            }
        }
    }
}
