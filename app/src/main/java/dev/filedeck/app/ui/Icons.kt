package dev.filedeck.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import dev.filedeck.app.FileKind

object Icons2 {
    val back = Icons.AutoMirrored.Filled.ArrowBack
    val check = Icons.Filled.Check
    val folderFilled = Icons.Filled.Folder
    val folder = Icons.Outlined.Folder
    val file = Icons.Outlined.Description
    val image = Icons.Outlined.Image
    val video = Icons.Outlined.Movie
    val audio = Icons.Outlined.MusicNote
    val archive = Icons.Outlined.FolderZip
    val pdf = Icons.Outlined.PictureAsPdf
    val apk = Icons.Outlined.Android
    val code = Icons.Outlined.Code
    val search = Icons.Outlined.Search
    val settings = Icons.Outlined.Settings
    val close = Icons.Outlined.Close
    val selectAll = Icons.Outlined.SelectAll
    val copy = Icons.Outlined.ContentCopy
    val cut = Icons.Outlined.ContentCut
    val share = Icons.Outlined.Share
    val delete = Icons.Outlined.Delete
    val more = Icons.Outlined.MoreVert
    val add = Icons.Outlined.Add
    val folderPlus = Icons.Outlined.CreateNewFolder
    val filePlus = Icons.Outlined.NoteAdd
    val edit = Icons.Outlined.Edit
    val info = Icons.Outlined.Info
    val external = Icons.AutoMirrored.Outlined.OpenInNew
    val grid = Icons.Outlined.GridView
    val list = Icons.Outlined.ViewAgenda
    val drive = Icons.Outlined.Storage
    val sd = Icons.Outlined.SdCard
    val sparkle = Icons.Outlined.AutoAwesome
    val download = Icons.Outlined.Download
    val camera = Icons.Outlined.CameraAlt
    val eye = Icons.Outlined.Visibility
    val eyeOff = Icons.Outlined.VisibilityOff
    val unzip = Icons.Outlined.Unarchive
    val layers = Icons.Outlined.Layers
    val chevron = Icons.Outlined.ChevronRight
    val lock = Icons.Outlined.Lock
    val refresh = Icons.Outlined.Refresh
}

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.Folder -> Icons2.folder
    FileKind.Image -> Icons2.image
    FileKind.Video -> Icons2.video
    FileKind.Audio -> Icons2.audio
    FileKind.Archive -> Icons2.archive
    FileKind.Pdf -> Icons2.pdf
    FileKind.Apk -> Icons2.apk
    FileKind.Doc, FileKind.Text -> Icons2.file
    FileKind.Code -> Icons2.code
    FileKind.File -> Icons2.file
}
