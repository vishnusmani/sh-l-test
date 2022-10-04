import jenkins.model.Jenkins
import hudson.model.*

node{
	properties ([pipelineTriggers([cron('*/5 * * * *')])])
	try{

        stage('Compile') {
                gradlew('clean', 'classes')
        }
        stage('Unit Tests') {
			try{
                gradlew('test')
			}
            catch(Exception ex){
			
			}
            finally{
                junit '**/build/test-results/test/TEST-*.xml'
            }
        }
        stage('Long-running Verification') {
            def SONAR_LOGIN = credentials('SONARCLOUD_TOKEN') //should be env var
            def stages = [:]
            
            stages["Integration Tests"] = {
                    try{
						gradlew('integrationTest')
						}
					catch(Exception ex){}
                    finally{
                            junit '**/build/test-results/integrationTest/TEST-*.xml'
                    }
                }
                stages["Code Analysis"] = {
                        gradlew('sonarqube')
                }
            parallel(stages)
        }
        stage('Assemble') {
                gradlew('assemble')
                stash includes: '**/build/libs/*.war', name: 'app'
        }
        stage('Promotion') {
                timeout(time: 1, unit:'DAYS') {
                    input 'Deploy to Production?'
                }
        }
        stage('Deploy to Production') {
                env.HEROKU_API_KEY = credentials('HEROKU_API_KEY')
                unstash 'app'
                gradlew('deployHeroku')
        }
		}
    catch(Exception ex){
            mail to: 'john.doe@mycompany.com', subject: 'Build failed', body: 'Please fix!'
    }
}

def gradlew(String... args) {
    sh "./gradlew ${args.join(' ')} -s"
}
