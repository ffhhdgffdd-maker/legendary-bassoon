# بنية WolFox Android المطابقة لمشروع iOS

هذا المشروع مخصص لتطبيقات WolFox التجريبية فقط، وتمنع `ModuleConfig` التشغيل خارج:

- `com.wolfox.testapp`
- `com.wolfox.sandbox`

## المطابقة

| iOS | Android |
|---|---|
| SniperGate | gate / LicenseManager |
| SniperAuth | network + activation |
| PhantomID | identity test profile |
| GPS_HK.framework | features/location + hooks |
| GPSHookManager | GPSMockManager |
| CLLocationManagerHooksInstall | LocationManager hooks |
| Sniper UI | ui/WolFoxPanel |
| WFSniperGateBridge | DirectBootstrap |

## ملفات الإصدار

- SAFE: موقع تجريبي واحد، بحث، مفضلة وسجل.
- CAMERA: ملف مختبر الكاميرا، ولا يستبدل بث الكاميرا في تطبيقات خارج المختبر.
- MULTI: ملفات مواقع متعددة مع اختيار يدوي وجدولة داخل تطبيق الاختبار.

الحماية ليست اختيارية: أي حزمة خارج قائمة السماح لا يتم تركيب أي Hook عليها.
