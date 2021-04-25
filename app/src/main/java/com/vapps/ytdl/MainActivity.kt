package com.vapps.ytdl

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.animation.LayoutTransition
import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.Window
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.DownloadProgressCallback
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import java.io.File


class MainActivity : AppCompatActivity()
{
    var is_Downloading = false
    var full_path: String? = null
    var tmpFilePath: File? =null
    var tmpFile: File? =null
    var pg_bar: ProgressBar? = null
    var tv_eta: TextView? = null
    var tv_eta2: TextView? = null
    var url_dl: TextView? = null
    var dl_p: View? = null
    var dl_p2: ViewGroup? = null
    var load_dialog: Dialog?=null
    var folder_location: String? = null
    var folder_location_uri: String? = null
    var tv_saveto: TextView? = null
    var info_sv: TextView? = null
    var eturl: TextInputLayout? = null
    var numbers_dl: Int = 0
    var is_pasta = false
    var url: String? = null


    fun show_Dialog(title: String, message: String, btntxt: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_error)
        val body = dialog.findViewById(R.id.body) as TextView
        body.text = title
        val body2 = dialog.findViewById(R.id.body2) as TextView
        body2.text = message
        val yesBtn = dialog.findViewById(R.id.yesBtn) as Button
        yesBtn.text = btntxt
        yesBtn.setOnClickListener {
            if (btntxt == "EXIT")
            {
                dialog.dismiss()
                System.exit(0);
            }
            if (btntxt == "OK")
            {
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    fun show_Load(message: String) {
        load_dialog = Dialog(this)
        load_dialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        load_dialog!!.setCancelable(false)
        load_dialog!!.setContentView(R.layout.loading_window)
        val message_1 = load_dialog!!.findViewById(R.id.message1) as TextView
        message_1.text = message

        load_dialog!!.show()
        load_dialog!!.window?.setBackgroundDrawableResource(android.R.color.transparent)
        load_dialog!!.window?.setGravity(Gravity.BOTTOM)
    }


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_layout)

        requestPermission()

        eturl  = findViewById<TextInputLayout>(R.id.et_url)
        val sharedPref = this?.getPreferences(Context.MODE_PRIVATE) ?: return
        folder_location =  sharedPref.getString("dl_folder", null)


        tv_saveto = findViewById<TextView>(R.id.saveto)
        info_sv = findViewById<TextView>(R.id.info_save)



        if(folder_location == null)
        {
            tv_saveto?.text = "Please Select a Folder to save Downloaded Files"
            info_sv?.text = "Warning!"
        }
        else
        {
            tv_saveto?.visibility = GONE
            info_sv?.visibility = GONE
        }


        dl_p = findViewById<View>(R.id.dl_layout)
        dl_p2 = findViewById<ViewGroup>(R.id.dl_layout)
        dl_p2?.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        dl_p?.visibility = View.INVISIBLE
        url_dl = eturl?.editText

        try
        {
            YoutubeDL.getInstance().init(application)
            FFmpeg.getInstance().init(application)

        }
        catch (e: Exception)
        {
            val errorE = e
            show_Dialog("🤔 ERROR", "Exception Raised! : " + errorE, "EXIT")
        }
    }

    fun startDownload(v: View)
    {
        show_Load("Sending Request, Finding Best Quality Available.")

        if (is_Downloading) {
            load_dialog?.dismiss()
            show_Dialog("🤔 ERROR", "A Download Is Already In Progress", "OK")
            return
        }
        is_Downloading = true

        if (is_pasta == false)
        {
            url = url_dl?.text.toString().trim()
        }
        else
        {
            is_pasta = false
        }


        if (folder_location == null)
        {
            load_dialog?.dismiss()
            is_Downloading = false
            show_Dialog("Storage Error", "Please Choose a Folder to save your Downloads", "OK")
            eturl?.helperText = ""
            return
        }


        if(url == "")
        {
            load_dialog?.dismiss()
            is_Downloading = false
            show_Dialog("🤔 ERROR", "URL Cannot be Empty", "OK")
        }
        else
        {
            dl_p?.visibility = View.VISIBLE
            tv_eta = findViewById<TextView>(R.id.tv_status)
            tv_eta2 = findViewById<TextView>(R.id.tv_status2)
            pg_bar = findViewById<ProgressBar>(R.id.progress_bar)
            val request = YoutubeDLRequest(url)
            tmpFile = File(folder_location)
            tmpFilePath = this.cacheDir
            //Toast.makeText(this, tmpFilePath?.absolutePath, Toast.LENGTH_LONG).show()

            val callback = DownloadProgressCallback { progress, etaInSeconds ->
                runOnUiThread {
                    if (progress.toFloat() >= 0.001F)
                    {
                        load_dialog?.dismiss()
                        if (progress.toFloat() >= 99.7F)
                        {
                            show_Load("Saving File")
                        }
                    }

                    pg_bar?.setProgress(progress.toInt())
                    tv_eta?.setText("${progress.toInt()}%")
                    if (etaInSeconds.toInt()  > 60)
                    {
                        tv_eta2?.setText("${etaInSeconds.toInt() / 60} Min")
                    }
                    else
                    {
                        tv_eta2?.setText("${etaInSeconds.toInt()} Sec")
                    }

                }

            }


            suspend fun download_file()
            {
                withContext(Dispatchers.IO)
                {
                    request.addOption("--extract-audio")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--no-part")
                    request.addOption("--no-continue")
                    request.addOption("--audio-quality", 0)
                    request.addOption("-o", tmpFilePath?.absolutePath + "/%(title)s" + "_YTDL" + ".%(ext)s")
                    try
                    {
                        YoutubeDL.getInstance().execute(request, callback)
                        copy_file(folder_location!!)
                    }
                    catch (e: Exception)
                    {
                        runOnUiThread {
                            load_dialog?.dismiss()
                            show_Dialog(
                                    "🤔 Error",
                                    "Failed to initiate Request. An Exception was Raised : " + e.toString() + " TRY AGAIN LATER!",
                                    "OK"
                            )
                        }
                    }

                    runOnUiThread {
                        is_Downloading = false
                        numbers_dl++
                        dl_p?.visibility = View.INVISIBLE
                        pg_bar?.setProgress(0)
                        tv_eta?.setText("0%")
                        tv_eta2?.setText("Loading")
                        load_dialog?.dismiss()
                        eturl?.helperText = ""
                    }

                }

            }

                GlobalScope.launch {
                    download_file()
                }

        }

    }

    fun setup_folder(v: View)
    {
        val OPEN_DIRECTORY_REQUEST_CODE = 42070
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE)

    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            42070 -> {
                if (resultCode == Activity.RESULT_OK) {
                    var path_uri = data?.data
                    //var actual_path = findFullPath(path_uri!!)
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    updateDefaultDownloadLocation(path_uri.toString())

                }
            }
        }
    }

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            42079 -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let {
                        this.contentResolver?.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )

                        updateDefaultDownloadLocation(it.toString())
                    }
                }
            }
        }
    }*/


    fun updateDefaultDownloadLocation(loc: String)
    {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString("dl_folder", loc)
            folder_location = loc
            folder_location_uri = loc
            tv_saveto?.visibility = GONE
            info_sv?.visibility = GONE
            apply()
        }

    }

    fun copy_file(downloadDir: String)
    {
        var destUri: Uri? = null
        try {


            val treeUri = Uri.parse(downloadDir)

            val docId = DocumentsContract.getTreeDocumentId(treeUri)

            val destDir = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)


            val dir: File = File(this.getCacheDir().getAbsolutePath())
            if (dir.exists())
            {
                for (f in dir.listFiles()) {

                    val mimeType =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "*/*"
                    destUri = DocumentsContract.createDocument(
                            this.contentResolver,
                            destDir,
                            mimeType,
                            f.name
                    )
                    val ins = f.inputStream()
                    val ops = this.contentResolver.openOutputStream(destUri!!)
                    IOUtils.copy(ins, ops)
                    IOUtils.closeQuietly(ops)
                    IOUtils.closeQuietly(ins)
                    f.delete()
                }
            }

        }
        catch (e: Exception)
        {
            Log.d("X", e.toString())
        }
        /*finally {
            tmpFile?.deleteRecursively()
        }*/
    }


    fun paste_data(v: View)
    {
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val pasta = clipboard.primaryClip
        if(clipboard.hasPrimaryClip())
        {
            var bg_layout = findViewById<ViewGroup>(R.id.main_bg)
            bg_layout.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            eturl?.helperText = "URL Pasted! \n" + pasta?.getItemAt(0)?.text
            url = (pasta?.getItemAt(0)?.text).toString().trim()
            Toast.makeText(this@MainActivity, "URL Pasted!", Toast.LENGTH_LONG).show()
            is_pasta = true
        }
    }


    private fun requestPermission() {
        if (!checkPermission())
        {
            requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), 1)
        }

    }

    private fun checkPermission(): Boolean {
        return if (SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result1 = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
            result1 == PackageManager.PERMISSION_GRANTED
        }
    }


}

fun findFullPath(path: String): String? {
    var path = path
    var actualResult = ""
    path = path.substring(5)
    var index = 0
    val result = StringBuilder("/storage")
    run {
        var i = 0
        while (i < path.length) {
            if (path[i] != ':') {
                result.append(path[i])
            } else {
                index = ++i
                result.append('/')
                break
            }
            i++
        }
    }
    for (i in index until path.length) {
        result.append(path[i])
    }
    actualResult = if (result.substring(9, 16).equals("primary", ignoreCase = true)) {
        result.substring(0, 8) + "/emulated/0/" + result.substring(17)
    } else {
        result.toString()
    }
    return actualResult
}





