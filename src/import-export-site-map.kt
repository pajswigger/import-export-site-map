package burp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.awt.Frame
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.concurrent.thread


val gson = GsonBuilder().registerTypeAdapter(ByteArray::class.java, ByteArrayAdapter()).create()


class BurpExtender: IBurpExtender {
    companion object {
        lateinit var callbacks: IBurpExtenderCallbacks
    }
    override fun registerExtenderCallbacks(callbacks: IBurpExtenderCallbacks) {
        Companion.callbacks = callbacks
        callbacks.setExtensionName("Import and export Site Map")
        callbacks.registerContextMenuFactory(ContextMenuFactory())
    }
}


class ContextMenuFactory: IContextMenuFactory {
    override fun createMenuItems(invocation: IContextMenuInvocation): List<JMenuItem> {
        if(invocation.invocationContext == IContextMenuInvocation.CONTEXT_TARGET_SITE_MAP_TREE) {
            val exportSiteMap = JMenuItem("Export Site Map")
            exportSiteMap.addActionListener(ExportSiteMap())
            val importSiteMap = JMenuItem("Import Site Map")
            importSiteMap.addActionListener(ImportSiteMap())
            return listOf(importSiteMap, exportSiteMap)
        }
        return emptyList()
    }
}


class ExportSiteMap: ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
        if(fileChooser.showSaveDialog(getBurpFrame()) == JFileChooser.APPROVE_OPTION) {
            val progressDialog = ProgressDialog(getBurpFrame(), true)
            progressDialog.setLocationRelativeTo(getBurpFrame())
            SwingUtilities.invokeLater {
                progressDialog.isVisible = true
            }
            thread {
                try {
                    try {
                        val siteMap = BurpExtender.callbacks.getSiteMap("").map { PersistHttpRequestResponse(it) }
                        val fileWriter = FileWriter(fileChooser.selectedFile)
                        gson.toJson(siteMap, fileWriter)
                        fileWriter.close()
                    } finally {
                        SwingUtilities.invokeLater {
                            progressDialog.isVisible = false
                        }
                    }
                }
                catch(e: Exception) {
                    JOptionPane.showMessageDialog(getBurpFrame(), e.toString(), "Export Failed", JOptionPane.WARNING_MESSAGE)
                }
            }
        }
    }
}


class ImportSiteMap: ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("JSON files", "json")
        if(fileChooser.showOpenDialog(getBurpFrame()) == JFileChooser.APPROVE_OPTION) {
            val progressDialog = ProgressDialog(getBurpFrame(), true)
            progressDialog.setLocationRelativeTo(getBurpFrame())
            SwingUtilities.invokeLater {
                progressDialog.isVisible = true
            }
            thread {
                try {
                    try {
                        val turnsType = object : TypeToken<List<PersistHttpRequestResponse>>() {}.type
                        val mapItems = gson.fromJson<List<PersistHttpRequestResponse>>(JsonReader(FileReader(fileChooser.selectedFile)), turnsType)
                        for (mapItem in mapItems) {
                            BurpExtender.callbacks.addToSiteMap(mapItem)
                        }
                    } finally {
                        SwingUtilities.invokeLater {
                            progressDialog.isVisible = false
                        }
                    }
                }
                catch(e: Exception) {
                    JOptionPane.showMessageDialog(getBurpFrame(), e.toString(), "Import Failed", JOptionPane.WARNING_MESSAGE)
                }
            }
        }
    }
}


class PersistHttpRequestResponse(o: IHttpRequestResponse): IHttpRequestResponse {
    override var comment = o.comment
    override var highlight = o.highlight
    @Transient
    override var httpService: IHttpService = o.httpService
        get() = httpService_
    @SerializedName("httpService")
    var httpService_ = PersistHttpService(o.httpService)
    override var request = o.request
    override var response = o.response
}


class PersistHttpService(o: IHttpService): IHttpService {
    override val host = o.host
    override val port = o.port
    override val protocol = o.protocol
}


fun getBurpFrame(): JFrame {
    return Frame.getFrames().filter{ it.isVisible && it.title.startsWith("Burp Suite")}[0] as JFrame
}


class ByteArrayAdapter: TypeAdapter<ByteArray>() {
    override fun write(writer: JsonWriter, data: ByteArray?) {
        if (data == null) {
            writer.nullValue()
        } else {
            val base64 = String(Base64.getEncoder().encode(data))
            writer.value(base64)
        }
    }

    override fun read(reader: JsonReader): ByteArray {
        return Base64.getDecoder().decode(reader.nextString())
    }
}
