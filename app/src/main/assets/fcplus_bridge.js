(function () {
  if (window.__fcplusAndroidBridgeV1) return;
  window.__fcplusAndroidBridgeV1 = true;

  function sendPage() {
    try {
      if (window.FCPlusAndroid && FCPlusAndroid.onPageState) {
        FCPlusAndroid.onPageState(document.title || "", location.href || "");
      }
    } catch (_) {}
  }

  function securityReason() {
    var text = (document.body && document.body.innerText || "").toLowerCase();
    var flags = [
      "captcha",
      "too many actions",
      "temporarily unavailable",
      "try again later",
      "access has been restricted",
      "verify your identity",
      "security verification"
    ];
    for (var i = 0; i < flags.length; i++) {
      if (text.indexOf(flags[i]) >= 0) return flags[i];
    }
    return "";
  }

  function inspect() {
    sendPage();
    var reason = securityReason();
    if (reason) {
      try {
        if (window.FCPlusAndroid && FCPlusAndroid.onSecurityStop) {
          FCPlusAndroid.onSecurityStop(reason);
        }
      } catch (_) {}
    }
  }

  var observer = new MutationObserver(function () {
    clearTimeout(window.__fcplusAndroidTimer);
    window.__fcplusAndroidTimer = setTimeout(inspect, 300);
  });

  if (document.documentElement) {
    observer.observe(document.documentElement, {
      subtree: true,
      childList: true,
      characterData: true
    });
  }

  inspect();
  setInterval(inspect, 5000);
})();