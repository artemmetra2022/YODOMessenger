/**
 * YODO Admin — экран блокировки в стиле Windows 11.
 *
 * Это первый замок перед админ-панелью: полный экран с часами, аватаром
 * и полем пароля (как на экране входа Windows 11). После разблокировки
 * по-прежнему требуется обычный вход аккаунта администратора через
 * Firebase Auth (см. admin.js), т.е. реальная защита остаётся там.
 *
 * Пароль:
 *  - по умолчанию 8213;
 *  - хранится как SHA-256-хеш в localStorage ("yodoAdminLockHash") и
 *    действует для конкретного браузера. В небезопасном контексте
 *    (локальный file://, где нет crypto.subtle) используется запасной
 *    лёгкий хеш — он защищает лишь от случайного подглядывания и не
 *    криптостойкий;
 *  - СМЕНА ПАРОЛЯ — внутри панели, в разделе «Обзор → Экран блокировки»
 *    (форма form-admin-lock в admin.html). Там администратор уже прошёл
 *    вход в аккаунт, поэтому текущий пароль повторно не спрашивается;
 *  - разблокировка запоминается в рамках вкладки (sessionStorage),
 *    обновление страницы не просит пароль повторно;
 *  - сброс на дефолт: localStorage.removeItem("yodoAdminLockHash").
 */

(function () {
  "use strict";

  var LOCK_HASH_KEY = "yodoAdminLockHash";
  var LOCK_SESSION_KEY = "yodoAdminUnlocked";
  var DEFAULT_PASSWORD = "8213";
  var MIN_PASSWORD_LENGTH = 4;

  var $ = function (id) { return document.getElementById(id); };

  /* ------------------------------------------------------------------ */
  /* Хеш пароля                                                          */
  /* ------------------------------------------------------------------ */

  /** SHA-256 (hex) — для сравнения пароля, введённого на экране блокировки. */
  function hashPassword(password) {
    // В https-контексте есть crypto.subtle — настоящий SHA-256.
    if (window.isSecureContext && window.crypto && window.crypto.subtle) {
      return window.crypto.subtle
        .digest("SHA-256", new TextEncoder().encode(password))
        .then(function (digest) {
          var bytes = new Uint8Array(digest);
          var hex = "";
          for (var i = 0; i < bytes.length; i++) {
            hex += (bytes[i] < 16 ? "0" : "") + bytes[i].toString(16);
          }
          return hex;
        })
        .catch(function () { return simpleHash(password); });
    }
    // file:// и прочие небезопасные контексты: crypto.subtle недоступен.
    return Promise.resolve(simpleHash(password));
  }

  /** Запасной лёгкий хеш (только для локальной отладки без https). */
  function simpleHash(value) {
    var h1 = 0x811c9dc5;
    var h2 = 0x1000193;
    for (var i = 0; i < value.length; i++) {
      var c = value.charCodeAt(i);
      h1 = ((h1 ^ c) * 0x01000193) >>> 0;
      h2 = ((h2 + c * 31 + (h2 << 5)) >>> 0) ^ h1;
    }
    return "fnv-" + h1.toString(16) + "-" + h2.toString(16);
  }

  /** Хеш действующего пароля: из localStorage либо хеш дефолта 8213.
   *  Возвращает Promise — дефолт считается той же hashPassword(), что и
   *  ввод (SHA-256 на проде, запасной хеш на file://), иначе на проде
   *  дефолт-пароль не совпадал бы с вводом. */
  function getStoredHash() {
    var stored = localStorage.getItem(LOCK_HASH_KEY);
    if (stored) return Promise.resolve(stored);
    return hashPassword(DEFAULT_PASSWORD);
  }

  /* ------------------------------------------------------------------ */
  /* Часы на экране                                                      */
  /* ------------------------------------------------------------------ */

  function startClock() {
    var timeEl = $("lock-clock-time");
    var dateEl = $("lock-clock-date");
    if (!timeEl || !dateEl) return;

    function tick() {
      var now = new Date();
      timeEl.textContent = String(now.getHours()).padStart(2, "0") + ":" +
        String(now.getMinutes()).padStart(2, "0");
      dateEl.textContent = now.toLocaleDateString("ru-RU", {
        weekday: "long", day: "numeric", month: "long",
      });
    }
    tick();
    setInterval(tick, 1000);
  }

  /* ------------------------------------------------------------------ */
  /* Показ/скрытие                                                       */
  /* ------------------------------------------------------------------ */

  var lockScreen;

  function hideLockScreen() {
    if (!lockScreen) return;
    // Смахивание вверх (см. .lock-screen.lock-out в admin-lock.css):
    // экран уезжает наверх ~0.62s и открывает админку под собой.
    lockScreen.classList.add("lock-out");
    setTimeout(function () {
      lockScreen.hidden = true;
      lockScreen.classList.remove("lock-out");
      // Сбрасываем внутренние transform-анимации к базовому состоянию.
      lockScreen.querySelectorAll(".lock-panel, .lock-top, .lock-bg").forEach(function (el) {
        el.style.transform = "";
        el.style.opacity = "";
      });
    }, 660);
  }

  function unlock() {
    try { sessionStorage.setItem(LOCK_SESSION_KEY, "1"); } catch (e) { /* приватный режим */ }
    hideLockScreen();
    // Передаём фокус в форму входа Firebase-аккаунта под блокировкой
    // после того, как экран блокировки уехал вверх.
    var loginInput = $("login-email");
    if (loginInput) setTimeout(function () { loginInput.focus(); }, 640);
  }

  /* ------------------------------------------------------------------ */
  /* Фейковая «зарядка» поля перед вводом пароля                         */
  /* ------------------------------------------------------------------ */

  var bootDone = false;

  /** Заливка полосы внутри поля 0→100% с процентами, как при зарядке.
   *  Пока идёт — поле readonly, ввод закрыт. По завершении поле открыто. */
  function startBootCharge() {
    bootDone = false;
    var wrap = document.querySelector(".lock-field-wrap");
    var bar = $("lock-progress");
    var input = $("lock-password");
    if (!wrap || !bar || !input) { bootDone = true; return; }

    bar.hidden = false;
    bar.style.width = "0%";
    wrap.classList.add("lock-charging");
    input.readOnly = true;
    input.placeholder = "Загрузка… 0%";
    clearError();

    var percent = 0;
    var timer = setInterval(function () {
      // Неровный прирост в духе реальной зарядки телефона.
      percent += 3 + Math.random() * 5;
      if (percent >= 100) {
        clearInterval(timer);
        percent = 100;
        bar.style.width = "100%";
        input.placeholder = "Загрузка… 100%";
        setTimeout(function () {
          bar.classList.add("lock-progress-done");
          setTimeout(function () {
            bar.hidden = true;
            bar.classList.remove("lock-progress-done");
          }, 320);
          wrap.classList.remove("lock-charging");
          input.readOnly = false;
          input.placeholder = "Пароль";
          bootDone = true;
          input.focus();
        }, 230);
        return;
      }
      bar.style.width = percent + "%";
      input.placeholder = "Загрузка… " + Math.round(percent) + "%";
    }, 90);
  }

  /* ------------------------------------------------------------------ */
  /* Ввод пароля на экране блокировки                                    */
  /* ------------------------------------------------------------------ */

  function showError(message) {
    var el = $("lock-error");
    el.textContent = message;
    el.className = "lock-error";
    el.hidden = false;
    // Тряска поля при ошибке.
    var wrap = document.querySelector(".lock-field-wrap");
    if (wrap) {
      wrap.classList.remove("lock-shake");
      // Перезапуск анимации при повторных промахах.
      void wrap.offsetWidth;
      wrap.classList.add("lock-shake");
    }
  }

  function clearError() {
    var el = $("lock-error");
    el.hidden = true;
    el.textContent = "";
    el.className = "lock-error";
    var wrap = document.querySelector(".lock-field-wrap");
    if (wrap) wrap.classList.remove("lock-shake");
  }

  function bindEnterForm() {
    var form = $("form-lock");
    var input = $("lock-password");

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      // Пока идёт «зарядка» поля — вход недоступен.
      if (!bootDone) {
        showError("Секунду, панель ещё загружается…");
        return;
      }
      var entered = input.value.trim();
      if (!entered) {
        showError("Введите пароль.");
        return;
      }
      hashPassword(entered).then(function (computed) {
        Promise.all([getStoredHash(), hashPassword(DEFAULT_PASSWORD)]).then(function (hashes) {
          var expected = hashes[0];
          var isDefault = hashes[0] === hashes[1];
          if (computed === expected) {
            clearError();
            input.value = "";
            unlock();
            return;
          }
          // Подсказка, если замок ещё на дефолтном 8213.
          var message = isDefault
            ? "Неверный пароль. По умолчанию используется 8213."
            : "Неверный пароль. Попробуйте ещё раз.";
          showError(message);
          input.value = "";
          input.focus();
        });
      });
    });

    // пока печатаем — ошибка не висит
    input.addEventListener("input", clearError);

    // глазик: показать/скрыть пароль (появляется, когда что-то введено)
    var eye = $("btn-lock-eye");
    eye.addEventListener("click", function () {
      var show = input.type === "password";
      input.type = show ? "text" : "password";
      eye.classList.toggle("lock-eye-on", show);
    });
    input.addEventListener("input", function () {
      eye.hidden = input.value.length === 0;
      if (eye.hidden) {
        input.type = "password";
        eye.classList.remove("lock-eye-on");
      }
    });
  }

  /* ------------------------------------------------------------------ */
  /* Смена пароля из панели (обзор → Экран блокировки)                   */
  /* ------------------------------------------------------------------ */

  function setPanelStatus(message, ok) {
    var el = $("admin-lock-status");
    if (!el) return;
    el.textContent = message;
    el.className = "card-hint " + (ok ? "lock-status-ok" : "lock-status-err");
  }

  function bindPanelChangeForm() {
    var form = $("form-admin-lock");
    if (!form) return; // разметка карточки отсутствует

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      var next = $("admin-lock-new").value;
      var repeat = $("admin-lock-repeat").value;

      if (next.length < MIN_PASSWORD_LENGTH) {
        setPanelStatus("Пароль слишком короткий — минимум 4 символа.", false);
        return;
      }
      if (next !== repeat) {
        setPanelStatus("Пароли не совпадают.", false);
        return;
      }

      Promise.all([getStoredHash(), hashPassword(DEFAULT_PASSWORD)]).then(function (hashes) {
        var lockOnDefault = hashes[0] === hashes[1];

        if (next === DEFAULT_PASSWORD && !lockOnDefault) {
          setPanelStatus("Нельзя возвращать пароль по умолчанию 8213 — придумайте свой.", false);
          return;
        }

        hashPassword(next).then(function (nextHash) {
          try {
            localStorage.setItem(LOCK_HASH_KEY, nextHash);
          } catch (e) {
            setPanelStatus("Не удалось сохранить пароль (нет доступа к локальному хранилищу).", false);
            return;
          }
          form.reset();
          setPanelStatus("Пароль экрана блокировки обновлён. Действует при следующем открытии панели в этом браузере.", true);
        });
      });
    });

    ["admin-lock-new", "admin-lock-repeat"].forEach(function (id) {
      $(id).addEventListener("input", function () {
        setPanelStatus("", true);
      });
    });
  }

  /* ------------------------------------------------------------------ */
  /* Запуск                                                              */
  /* ------------------------------------------------------------------ */

  function init() {
    lockScreen = $("screen-lock");
    startClock();
    bindPanelChangeForm();

    if (lockScreen) {
      bindEnterForm();

      // Уже разблокировано в этой вкладке (например, обновление страницы).
      var alreadyUnlocked = false;
      try {
        alreadyUnlocked = sessionStorage.getItem(LOCK_SESSION_KEY) === "1";
      } catch (e) { /* приватный режим — показываем как обычно */ }
      if (alreadyUnlocked) {
        lockScreen.hidden = true;
      } else {
        // Фейковая «зарядка» поля: по завершении фокус в поле пароля.
        startBootCharge();
      }
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
