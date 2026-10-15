package app.yodo.messenger.features.canvas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun CanvasCollabPanel(vm:CanvasViewModel,state:CanvasCollabState,enabled:Boolean){
    val context=LocalContext.current
    var explain by remember{mutableStateOf(false)}
    var desiredHost by remember{mutableStateOf<Boolean?>(null)}
    var denied by remember{mutableStateOf(false)}
    val permissions=remember{
        buildList {
            if(Build.VERSION.SDK_INT>=31){add(Manifest.permission.BLUETOOTH_SCAN);add(Manifest.permission.BLUETOOTH_CONNECT);add(Manifest.permission.BLUETOOTH_ADVERTISE)}
            if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if(Build.VERSION.SDK_INT<=32){add(Manifest.permission.ACCESS_FINE_LOCATION);add(Manifest.permission.ACCESS_COARSE_LOCATION)}
        }.toTypedArray()
    }
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val host=desiredHost;desiredHost=null
        if(host!=null&&permissions.all{ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED})vm.startCollaboration(host)
        else denied=true
    }
    fun begin(host:Boolean){
        explain=false
        if(permissions.all{ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED})vm.startCollaboration(host)
        else{desiredHost=host;request.launch(permissions)}
    }
    val link=state.link
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
        when(link.phase){
            CanvasLinkPhase.IDLE->OutlinedButton(enabled=enabled,onClick={explain=true}){Text("Рисовать вместе · рядом")}
            CanvasLinkPhase.HOSTING->Text("Ждём участника · доска ${link.room.orEmpty().take(8)}")
            CanvasLinkPhase.SEARCHING->{
                Text(if(link.peers.isEmpty())"Ищем доски рядом…"else"Выберите доску:")
                link.peers.forEach{peer->OutlinedButton(onClick={vm.connectCanvasPeer(peer)}){Text("Доска ${peer.room.take(8)}")}}
            }
            CanvasLinkPhase.VERIFYING->Text("Сверьте код на обоих устройствах")
            CanvasLinkPhase.CONNECTING->Text("Ожидаем подтверждения подключения…")
            CanvasLinkPhase.CONNECTED->Text(if(state.syncing)"Передаём доску…"else if(state.pending)"Подтверждаем действие…"else"${if(link.host)"Ведущий"else"Участник"} · вместе · версия ${state.revision}")
        }
        if(link.phase!=CanvasLinkPhase.IDLE)TextButton(onClick={vm.stopCollaboration()}){Text("Отключиться")}
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text("Совместная доска Nearby")},text={Text("Для двух Android-устройств поблизости, не через интернет. Ведущий передаст всю текущую доску вместе с фоном; второй участник сможет сохранить её копию. Сверьте код лично на обоих экранах. Это проверяет подключение, но не аккаунт собеседника в чате. Офлайн-чат Nearby нужно закрыть.\n\nСоздать сессию с этой доской или найти доску рядом? Ваша текущая доска сохранится локально.")},
        confirmButton={TextButton(onClick={begin(true)}){Text("Создать сессию")}},dismissButton={TextButton(onClick={begin(false)}){Text("Найти рядом")}})
    if(link.phase==CanvasLinkPhase.VERIFYING)AlertDialog(onDismissRequest={vm.verifyCanvasPeer(false)},title={Text("Код подключения: ${link.code.orEmpty()}")},text={Text("На другом устройстве должен быть точно такой же код. Подтвердите только после личной сверки. Имя аккаунта этим кодом не проверяется.")},confirmButton={TextButton(onClick={vm.verifyCanvasPeer(true)}){Text("Код совпадает")}},dismissButton={TextButton(onClick={vm.verifyCanvasPeer(false)}){Text("Не совпадает / отмена")}})
    if(denied)AlertDialog(onDismissRequest={denied=false},title={Text("Нужны разрешения Nearby")},text={Text("Для совместной доски разрешите доступ к устройствам поблизости. На Android 12 и старше Nearby также требует разрешение и включённую геолокацию. Проверьте Bluetooth/Wi-Fi. Локальный редактор работает без этих разрешений.")},confirmButton={TextButton(onClick={denied=false}){Text("Понятно")}})
}
