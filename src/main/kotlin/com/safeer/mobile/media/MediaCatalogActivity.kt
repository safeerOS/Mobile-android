package com.safeer.mobile.media

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.safeer.mobile.R

class MediaCatalogActivity : Activity() {
    private lateinit var repository: UserMediaRepository
    private lateinit var itemsBox: LinearLayout
    private lateinit var typesBox: LinearLayout
    private lateinit var genresBox: LinearLayout
    private lateinit var status: TextView
    private var selectedType: MediaType? = null
    private var selectedGenre: String? = null
    private var visibleItems: List<UserMediaItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = UserMediaRepository(this)
        setContentView(buildScreen())
        load()
    }

    override fun onDestroy() { repository.close(); super.onDestroy() }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color); maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
            setBackgroundColor(getColor(R.color.os_ozadje))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text(30f, getColor(R.color.os_besedilo), true).apply { setText(R.string.os_user_media_title) })
        heading.addView(text(14f, getColor(R.color.os_umirjeno)).apply { setText(R.string.os_user_media_subtitle) })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton())
        root.addView(header)

        typesBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; setPadding(0, dp(18), 0, 0); addView(typesBox)
        })
        drawTypes()
        genresBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(genresBox)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        status = text(14f, getColor(R.color.os_umirjeno)).apply { setPadding(0, dp(12), 0, dp(10)) }
        root.addView(status)
        itemsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(itemsBox) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun actionButton() = TextView(this).apply {
        isFocusable = true; isClickable = true; gravity = Gravity.CENTER
        setTextColor(getColor(R.color.os_besedilo)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        text = "+  ${getString(R.string.os_user_media_add)}"
        setPadding(dp(18), dp(12), dp(18), dp(12)); background = focusBackground()
        setOnClickListener { chooseAddKind() }
    }

    private fun drawTypes() {
        typesBox.removeAllViews()
        typesBox.addView(chip(getString(R.string.os_user_media_all), selectedType == null) {
            selectedType = null; selectedGenre = null; load()
        })
        MediaType.entries.forEach { type ->
            typesBox.addView(chip(type.label(this), selectedType == type) { selectedType = type; selectedGenre = null; load() })
        }
    }

    private fun chip(label: String, selected: Boolean, click: () -> Unit) = TextView(this).apply {
        isFocusable = true; isClickable = true; gravity = Gravity.CENTER; text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (selected) getColor(R.color.os_ozadje) else getColor(R.color.os_besedilo))
        setPadding(dp(15), dp(9), dp(15), dp(9))
        background = if (selected) GradientDrawable().apply {
            cornerRadius = dp(18).toFloat(); setColor(getColor(R.color.os_mint))
        } else focusBackground()
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
    }

    private fun focusBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(getColor(R.color.os_kartica_dvignjena)); setStroke(dp(2), getColor(R.color.os_mint))
        })
        addState(intArrayOf(), GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(getColor(R.color.os_kartica_steklo)); setStroke(dp(1), getColor(R.color.os_kartica_obroba))
        })
    }

    private fun load() {
        drawTypes()
        status.setText(R.string.os_glasba_nalagam)
        repository.filter(selectedType, selectedGenre) { items ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                visibleItems = items
                drawGenres(items)
                drawItems(items)
            }
        }
    }

    private fun drawGenres(items: List<UserMediaItem>) {
        val keys = items.flatMap { it.genreKeys() }.distinct().sortedBy { Genre.label(it) }
        if (selectedGenre != null && selectedGenre !in keys) selectedGenre = null
        genresBox.removeAllViews()
        genresBox.addView(chip(getString(R.string.os_user_media_all_genres), selectedGenre == null) {
            selectedGenre = null; load()
        })
        keys.forEach { key -> genresBox.addView(chip(genreLabel(key), selectedGenre == key) { selectedGenre = key; load() }) }
    }

    private fun genreLabel(key: String): String = Genre.entries.firstOrNull { it.name == key }?.label(this) ?: Genre.label(key)

    private fun drawItems(items: List<UserMediaItem>) {
        itemsBox.removeAllViews()
        if (items.isEmpty()) {
            status.setText(R.string.os_user_media_empty)
            itemsBox.addView(text(18f, getColor(R.color.os_umirjeno)).apply {
                setText(R.string.os_user_media_empty); gravity = Gravity.CENTER; setPadding(0, dp(70), 0, 0)
            })
            return
        }
        status.text = getString(R.string.os_user_media_count, items.size)
        val columns = 2
        items.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { item -> row.addView(card(item), LinearLayout.LayoutParams(0, dp(150), 1f).apply {
                marginEnd = dp(12); bottomMargin = dp(12)
            }) }
            repeat(columns - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dp(12) }) }
            itemsBox.addView(row)
        }
        itemsBox.post { if (itemsBox.findFocus() == null) itemsBox.getChildAt(0)?.let { (it as? LinearLayout)?.getChildAt(0)?.requestFocus() } }
    }

    private fun card(item: UserMediaItem) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        isFocusable = true; isClickable = true; background = focusBackground(); setPadding(dp(18), dp(14), dp(18), dp(14))
        val icon = ImageView(this@MediaCatalogActivity).apply {
            setImageResource(when (item.type) {
                MediaType.RADIO -> android.R.drawable.ic_dialog_info
                MediaType.LOCAL -> android.R.drawable.ic_input_get
                else -> android.R.drawable.ic_media_play
            })
            imageTintList = ColorStateList.valueOf(getColor(R.color.os_mint)); scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(15) })
        val labels = LinearLayout(this@MediaCatalogActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(18f, getColor(R.color.os_besedilo), true).apply { text = item.title })
        labels.addView(text(13f, getColor(R.color.os_mint)).apply { text = item.type.label(this@MediaCatalogActivity) })
        labels.addView(text(12f, getColor(R.color.os_umirjeno)).apply { text = item.genreKeys().joinToString(" · ") { genreLabel(it) } })
        item.description?.takeIf { it.isNotBlank() }?.let { description ->
            labels.addView(text(12f, getColor(R.color.os_umirjeno)).apply { text = description })
        }
        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        setOnClickListener { Toast.makeText(this@MediaCatalogActivity, item.title, Toast.LENGTH_SHORT).show() }
        setOnLongClickListener { confirmDelete(item); true }
    }

    private fun confirmDelete(item: UserMediaItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.os_user_media_delete)
            .setMessage(getString(R.string.os_user_media_delete_question, item.title))
            .setPositiveButton(R.string.os_mediji_odstrani) { _, _ -> repository.delete(item) { runOnUiThread { load() } } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseAddKind() {
        val choices = arrayOf(getString(R.string.os_user_media_add_url), getString(R.string.os_user_media_add_local))
        AlertDialog.Builder(this).setTitle(R.string.os_user_media_add)
            .setItems(choices) { _, which -> if (which == 0) showAddDialog() else pickLocalFile() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showAddDialog(presetUrl: String = "", presetTitle: String = "", mime: String? = null, local: Boolean = false) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(6), dp(24), 0) }
        fun field(hint: Int, value: String = "", multiline: Boolean = false) = EditText(this).apply {
            setHint(hint); setText(value); isSingleLine = !multiline
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            box.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        val title = field(R.string.os_user_media_title_hint, presetTitle)
        val types = MediaType.entries.toTypedArray()
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@MediaCatalogActivity, android.R.layout.simple_spinner_dropdown_item, types.map { it.label(this@MediaCatalogActivity) })
            setSelection(if (local) types.indexOf(MediaType.LOCAL) else types.indexOf(MediaType.MOVIE)); box.addView(this)
        }
        val genres = Genre.entries.toTypedArray()
        val chosenGenres = linkedSetOf(Genre.OTHER)
        val customGenre = field(R.string.os_user_media_custom_genre_hint)
        val url = field(R.string.os_user_media_url_hint, presetUrl).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI }
        if (local) url.isEnabled = false
        val license = field(R.string.os_user_media_license_hint).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI }

        AlertDialog.Builder(this).setTitle(R.string.os_user_media_add).setView(box)
            .setPositiveButton(R.string.os_mediji_dodaj_gumb) { _, _ ->
                val selectedGenres = chosenGenres.map { it.name }.toMutableList().apply {
                    customGenre.text.toString().takeIf { it.isNotBlank() }?.let { add(Genre.customKey(it)) }
                }.distinct().ifEmpty { listOf(Genre.OTHER.name) }
                val address = url.text.toString().trim()
                val chosenType = types[type.selectedItemPosition]
                val item = UserMediaItem(
                    title = title.text.toString().trim(), type = chosenType,
                    streamUrl = address, genres = selectedGenres.joinToString(","),
                    mimeType = mime, licenseUrl = license.text.toString().trim().ifBlank { null })
                if (!valid(item)) Toast.makeText(this, R.string.os_user_media_invalid, Toast.LENGTH_LONG).show()
                else importItem(item, local)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun valid(item: UserMediaItem): Boolean {
        if (item.title.isBlank()) return false
        val scheme = Uri.parse(item.streamUrl).scheme?.lowercase()
        return scheme in setOf("http", "https", "content", "file")
    }

    private fun importItem(item: UserMediaItem, local: Boolean) {
        repository.insert(item) { Toast.makeText(this, R.string.os_user_media_added, Toast.LENGTH_SHORT).show(); load() }
    }

    private fun pickLocalFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*", "application/vnd.apple.mpegurl", "audio/x-mpegurl"))
        }, PICK_FILE)
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        showAddDialog(uri.toString(), "Datoteka", contentResolver.getType(uri), local = true)
    }

    companion object {
        private const val PICK_FILE = 4107
    }
}
