package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.OutlinedButton
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun CanvasCollabPanel(vm:CanvasViewModel,state:CanvasCollabState,enabled:Boolean,controlsExpanded:Boolean=false,onDismiss:()->Unit={}){
    val context=LocalContext.current
    var explain by remember{mutableStateOf(false)}
    var desiredHost by remember{mutableStateOf<Boolean?>(null)}
    var denied by remember{mutableStateOf(false)}
    var onlineInfo by remember{mutableStateOf(false)}
    var enterCode by remember{mutableStateOf(false)}
    var typedCode by remember{mutableStateOf("")}
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
    LaunchedEffect(link.phase) { if(link.phase==CanvasLinkPhase.VERIFYING) onDismiss() }
    if(controlsExpanded) CanvasPickerSheet("Рисовать вместе",onDismiss){
        when(link.phase){
            CanvasLinkPhase.IDLE->{
                OutlinedButton(enabled=enabled,onClick={onDismiss();explain=true}){Text("Рисовать вместе · рядом")}
                OutlinedButton(enabled=enabled,onClick={onDismiss();onlineInfo=true}){Text("Рисовать вместе · онлайн")}
            }
            CanvasLinkPhase.HOSTING->if(link.online){
                Text("Код приглашения: ${link.code.orEmpty()}")
                OutlinedButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Присоединяйся к моей доске в YODO Messenger. Код приглашения: ${link.code.orEmpty()} (действует 10 минут).")}
                    context.startActivity(Intent.createChooser(send,"Пригласить").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }){Text("Поделиться кодом")}
            }else Text("Ждём участника · доска ${link.room.orEmpty().take(8)}")
            CanvasLinkPhase.SEARCHING->{
                Text(if(link.peers.isEmpty())"Ищем доски рядом…"else"Выберите доску:")
                link.peers.forEach{peer->OutlinedButton(onClick={vm.connectCanvasPeer(peer)}){Text("Доска ${peer.room.take(8)}")}}
            }
            CanvasLinkPhase.VERIFYING->Text(if(link.online)"«${link.peerName.orEmpty()}» просит присоединиться" else "Сверьте код на обоих устройствах")
            CanvasLinkPhase.CONNECTING->Text(if(link.online)"Ждём, пока ведущий подтвердит вход…" else "Ожидаем подтверждения подключения…")
            CanvasLinkPhase.CONNECTED->Text(if(state.syncing)"Передаём доску…"else if(state.pending)"Подтверждаем действие…"else"${if(link.host)"Ведущий"else"Участник"} · ${if(link.online)"онлайн"else"вместе"} · версия ${state.revision}")
        }
        if(link.phase!=CanvasLinkPhase.IDLE)TextButton(onClick={vm.stopCollaboration()}){Text("Отключиться")}
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text("Совместная доска Nearby")},text={Text("Для двух Android-устройств поблизости, не через интернет. Ведущий передаст всю текущую доску вместе с фоном; второй участник сможет сохранить её копию. Сверьте код лично на обоих экранах. Это проверяет подключение, но не аккаунт собеседника в чате. Офлайн-чат Nearby нужно закрыть.\n\nСоздать сессию с этой доской или найти доску рядом? Ваша текущая доска сохранится локально.")},
        confirmButton={TextButton(onClick={begin(true)}){Text("Создать сессию")}},dismissButton={TextButton(onClick={begin(false)}){Text("Найти рядом")}})
    if(onlineInfo)AlertDialog(onDismissRequest={onlineInfo=false},title={Text("Совместная доска онлайн")},
        text={Text("Работает через интернет на любом расстоянии, приложение должно оставаться открытым у обоих. Рисунок и фон проходят через сервер Firebase и НЕ защищены сквозным шифрованием; при завершении сессии данные удаляются. Приглашение — одноразовый код на 10 минут: передайте его собеседнику, затем разрешите ему войти. Ведущий — тот, кто создал приглашение; второй участник получит копию всей текущей доски вместе с фоном. Ваша текущая доска сохранится локально.")},
        confirmButton={TextButton(onClick={onlineInfo=false;vm.startOnlineCollaboration(true)}){Text("Создать приглашение")}},dismissButton={TextButton(onClick={onlineInfo=false;typedCode="";enterCode=true}){Text("Ввести код")}})
    if(enterCode)AlertDialog(onDismissRequest={enterCode=false},title={Text("Код приглашения")},
        text={OutlinedTextField(value=typedCode,onValueChange={typedCode=it.take(12)},singleLine=true,label={Text("Например, ABCD-EFGH")})},
        confirmButton={TextButton(enabled=CanvasInviteCode.normalize(typedCode)!=null,onClick={enterCode=false;vm.joinOnline(typedCode)}){Text("Присоединиться")}},dismissButton={TextButton(onClick={enterCode=false}){Text("Отмена")}})
    if(link.phase==CanvasLinkPhase.VERIFYING&&link.online)AlertDialog(onDismissRequest={vm.verifyCanvasPeer(false)},title={Text("Пустить «${link.peerName.orEmpty()}»?")},
        text={Text("Этот человек ввёл ваш код приглашения. Разрешайте, только если приглашали именно его: он получит копию всей текущей доски вместе с фоном. Имя вводит сам участник, оно не проверяется.")},
        confirmButton={TextButton(onClick={vm.verifyCanvasPeer(true)}){Text("Разрешить")}},dismissButton={TextButton(onClick={vm.verifyCanvasPeer(false)}){Text("Отклонить")}})
    if(link.phase==CanvasLinkPhase.VERIFYING&&!link.online)AlertDialog(onDismissRequest={vm.verifyCanvasPeer(false)},title={Text("Код подключения: ${link.code.orEmpty()}")},text={Text("На другом устройстве должен быть точно такой же код. Подтвердите только после личной сверки. Имя аккаунта этим кодом не проверяется.")},confirmButton={TextButton(onClick={vm.verifyCanvasPeer(true)}){Text("Код совпадает")}},dismissButton={TextButton(onClick={vm.verifyCanvasPeer(false)}){Text("Не совпадает / отмена")}})
    if(denied)AlertDialog(onDismissRequest={denied=false},title={Text("Нужны разрешения Nearby")},text={Text("Для совместной доски разрешите доступ к устройствам поблизости. На Android 12 и старше Nearby также требует разрешение и включённую геолокацию. Проверьте Bluetooth/Wi-Fi. Локальный редактор работает без этих разрешений.")},confirmButton={TextButton(onClick={denied=false}){Text("Понятно")}})
}
