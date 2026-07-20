package app.yodo.messenger.features.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoPrimary

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var nameInitialized by remember { mutableStateOf(false) }

    // Подставляем значения из загруженного профиля только один раз, чтобы не перетирать ввод пользователя
    LaunchedEffect(uiState.user) {
        if (!nameInitialized && uiState.user != null) {
            name = uiState.user?.displayName.orEmpty()
            username = uiState.user?.username.orEmpty()
            bio = uiState.user?.bio.orEmpty()
            nameInitialized = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadAvatar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    displayName = uiState.user?.displayName.orEmpty(),
                    photoUrl = uiState.user?.photoUrl,
                    avatarBase64 = uiState.user?.avatarBase64,
                    size = 96.dp
                )

                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(YodoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Изменить фото",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                singleLine = true
            )
            TextButton(
                onClick = { viewModel.updateDisplayName(name) },
                enabled = !uiState.isSavingName && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSavingName) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Сохранить имя")
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username (например, ivan_petrov)") },
                leadingIcon = { Text("@") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            TextButton(
                onClick = { viewModel.updateUsername(username) },
                enabled = !uiState.isSavingUsername && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSavingUsername) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Сохранить username")
                }
            }

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 150) bio = it },
                label = { Text("О себе") },
                placeholder = { Text("Пара слов о себе (до 150 символов)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 2,
                maxLines = 4
            )
            Text(
                text = "${bio.length}/150",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            TextButton(
                onClick = { viewModel.updateBio(bio) },
                enabled = !uiState.isSavingBio,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSavingBio) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Сохранить описание")
                }
            }

            uiState.user?.phoneNumber?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            uiState.user?.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = YodoError, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
