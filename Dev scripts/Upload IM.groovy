




String hostURI  = "http://jiratest-im84.stuxnet.se"
String restUser = "anders"
String restPw = restUser
String sourceFile = "src/customRiadaLibraries/insightmanager/InsightManagerForScriptrunner.groovy"
String jiraHome = "/var/atlassian/application-data/jiraim84/"
String destinationFileName = "customRiadaLibraries/insightmanager/InsightManagerForScriptrunner.groovy"

uploadIm(hostURI, restUser, restPw, sourceFile, jiraHome, destinationFileName)

void uploadIm(String hostURI, String restUser, String restPw, String sourceFilePath, String jiraHomePath, String destFileName) {

    println("Uploading file:" +sourceFilePath + " to:" + jiraHomePath)




    File sourceFile = new File(sourceFilePath)

    println("Path:" + System.getProperty("user.dir"))

    HttpURLConnection cacheClearConnection = new URL(hostURI + "/rest/scriptrunner/latest/idea/file?filePath=${URLEncoder.encode(destFileName, "UTF-8")}&rootPath=${ URLEncoder.encode(jiraHomePath + "scripts", "UTF-8")}").openConnection() as HttpURLConnection


    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    cacheClearConnection.setRequestProperty("Authorization", auth)
    cacheClearConnection.setDoOutput(true)
    cacheClearConnection.setRequestMethod("PUT")
    cacheClearConnection.setRequestProperty("Content-Type", "application/octet-stream")
    cacheClearConnection.setRequestProperty("Accept", "*/*")
    OutputStreamWriter out = new OutputStreamWriter(
            cacheClearConnection.getOutputStream());
    out.write(sourceFile.text.bytes.encodeBase64());
    out.close();





    //def rawReturn = new JsonSlurper().parse(cacheClearConnection.getInputStream())

    println("Cache clear output:" + cacheClearConnection.getInputStream())




}
