package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.repository.LinkPreview
import app.yodo.messenger.data.repository.LinkPreviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinkPreviewViewModel @Inject constructor(
    private val linkPreviewRepository: LinkPreviewRepository
) : ViewModel() {

    private val _previewsByUrl = MutableStateFlow<Map<String, LinkPreview?>>(emptyMap())
    val previewsByUrl: StateFlow<Map<String, LinkPreview?>> = _previewsByUrl

    private val requestedUrls = mutableSetOf<String>()

    fun requestPreview(url: String) {
        if (!requestedUrls.add(url)) return
        viewModelScope.launch {
            val preview = linkPreviewRepository.getPreview(url)
            _previewsByUrl.value = _previewsByUrl.value + (url to preview)
        }
    }
}
