import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.groovy.json.internal.LazyMap

/**
 * This is a script intended for developers, it's a bit of a dirty hack to upload IM and clear Class cashes in JIRA by using private Scriptrunner REST APIs
 */

String hostURI  = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser
String sourceFile = "src/customRiadaLibraries/insightmanager/InsightManagerForScriptrunner.groovy"
String jiraHome = "/var/atlassian/application-data/jiraim84/"
String destinationFileName = "customRiadaLibraries/insightmanager/InsightManagerForScriptrunner.groovy"

uploadIm(hostURI, restUser, restPw, sourceFile, jiraHome, destinationFileName)
clearCodeCache(hostURI, restUser, restPw)
subclassWorkaround(hostURI, restUser, restPw)

void uploadIm(String hostURI, String restUser, String restPw, String sourceFilePath, String jiraHomePath, String destFileName) {

    println("Uploading file:" +sourceFilePath + " to:" + jiraHomePath)




    File sourceFile = new File(sourceFilePath)

    println("Path:" + System.getProperty("user.dir"))

    HttpURLConnection imUploadConnection = new URL(hostURI + "/rest/scriptrunner/latest/idea/file?filePath=${URLEncoder.encode(destFileName, "UTF-8")}&rootPath=${ URLEncoder.encode(jiraHomePath + "scripts", "UTF-8")}").openConnection() as HttpURLConnection


    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    imUploadConnection.setRequestProperty("Authorization", auth)
    imUploadConnection.setDoOutput(true)
    imUploadConnection.setRequestMethod("PUT")
    imUploadConnection.setRequestProperty("Content-Type", "application/octet-stream")
    imUploadConnection.setRequestProperty("Accept", "*/*")
    OutputStreamWriter out = new OutputStreamWriter(
            imUploadConnection.getOutputStream());
    out.write(sourceFile.text.bytes.encodeBase64());
    out.close();


    println("IM upload HTTP response code:" + imUploadConnection.responseCode)



}


void clearCodeCache(String hostURI, String restUser, String restPw) {

    HttpURLConnection cacheClearConnection = new URL(hostURI + "/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches").openConnection() as HttpURLConnection
    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    cacheClearConnection.setRequestProperty("Authorization", auth)
    cacheClearConnection.setDoOutput(true)
    cacheClearConnection.setRequestMethod("POST")
    cacheClearConnection.setRequestProperty("Content-Type", "application/json")
    cacheClearConnection.setRequestProperty("Accept", "application/json")
    byte[] jsonByte = new JsonBuilder(["FIELD_WHICH_CACHE": "gcl", "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches"]).toPrettyString().getBytes("UTF-8")
    cacheClearConnection.outputStream.write(jsonByte, 0, jsonByte.length)


    LazyMap rawReturn = new JsonSlurper().parse(cacheClearConnection.getInputStream())

    println("Cache clear output:" + rawReturn.output)

}


void subclassWorkaround(String hostURI, String restUser, String restPw) {


    String script = "" +
            "import customRiadaLibraries.insightmanager.InsightManagerForScriptrunner\n" +
            "\n" +
            " customRiadaLibraries.insightmanager.InsightManagerForScriptrunner.SimplifiedAttachmentBean simplifiedAttachmentBean\n" +
            "\n" +
            " log.warn(\"SimplifiedAttachmentBean was imported\")"

    //script = URLEncoder.encode(script, "UTF-8")

    HttpURLConnection connection = new URL(hostURI + "/rest/scriptrunner/latest/user/exec/").openConnection() as HttpURLConnection
    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    connection.setRequestProperty("Authorization", auth)
    connection.setDoOutput(true)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("X-Atlassian-token" ,"no-check")
    byte[] jsonByte = new JsonBuilder(["script":script]).toPrettyString().getBytes("UTF-8")
    connection.outputStream.write(jsonByte, 0, jsonByte.length)


    LazyMap rawReturn = new JsonSlurper().parse(connection.getInputStream())

    println("Subclass workround output:" + rawReturn.snapshot.log)

}
