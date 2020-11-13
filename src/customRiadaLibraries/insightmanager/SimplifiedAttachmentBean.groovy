package customRiadaLibraries.insightmanager

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.util.JiraHome
import com.riadalabs.jira.plugins.insight.services.model.AttachmentBean

/**
 * <h3>This is a class intended to simplify working with Insight AttachmentBeans</h3>
 * Once instantiated it will give you easy access to the AttachmentBean it self as well as the related File object
 */
public class SimplifiedAttachmentBean {

    public AttachmentBean attachmentBean
    public Integer id
    public File attachmentFile
    public String originalFileName
    final static String jiraDataPath = ComponentAccessor.getComponentOfType(JiraHome).getDataDirectory().path

    SimplifiedAttachmentBean(AttachmentBean attachmentBean) {

        this.attachmentBean = attachmentBean
        this.id = attachmentBean.id
        this.attachmentFile = getFile(this.attachmentBean)
        this.originalFileName = attachmentBean.filename


    }

    /**
     * This method will give you the File object of an AttachmentBean
     * @param attachmentBean The AttachmentBean whose File object you want
     * @return A File object
     */
    static File getFile(AttachmentBean attachmentBean) {

        String expectedPath = jiraDataPath + "/attachments/insight/object/${attachmentBean.objectId}/" + attachmentBean.getNameInFileSystem()

        File attachmentFile = new File(expectedPath)
        assert attachmentFile.canRead(): "Cant access attachment file: " + attachmentBean.getNameInFileSystem()
        return attachmentFile
    }

    boolean isValid() {

        return this.attachmentBean != null && this.id > 0 && this.attachmentFile.canRead()

    }

    /**
     * Compare two SimplifiedAttachmentBean to determine if they are the same
     * @param other another SimplifiedAttachmentBean
     * @return true if equals, false if not
     */
    @Override
    boolean equals(def other) {
        SimplifiedAttachmentBean otherBean

        if (!other instanceof SimplifiedAttachmentBean) {
            return false
        }

        otherBean = other as SimplifiedAttachmentBean
        if (this.attachmentFile.getBytes().sha256() != otherBean.attachmentFile.getBytes().sha256()) {
            return false

        } else if (this.attachmentBean.nameInFileSystem != otherBean.attachmentBean.nameInFileSystem) {
            return false
        } else {

            return true
        }

    }


}
