# Changelog

## 1.2.0
- والپیپر تیره‌ی DoorAlert: مانیتور موقع خاموش شدن ماشین همه‌ی اپ‌ها رو می‌بنده، به‌جز اپ والپیپر. با فعال کردنش، DoorAlert بعد از هر بار خاموش و روشن کردن ماشین خودش فعال می‌مونه (Live wallpaper keeps DoorAlert alive across ignition cycles: the head unit force-stops every user app on ACC off except the live-wallpaper app)

## 1.1.0
- موقع تنظیم و توی «داده‌های خام»، همه‌ی داده‌های مانیتور خونده می‌شه، نه فقط `canbus.*` (Setup and raw view now scan every head-unit key, not only `canbus.*`)
- توی «داده‌های خام»، هر چیزی که تغییر کنه بالای لیست میاد (Changed values float to the top of the raw view)
- بعد از تنظیم، فقط همون چند کلید لازم خونده می‌شه تا سبک بمونه (After setup only the learned keys are polled)

## 1.0.0
- اولین نسخه: بنر هشدار باز موندن در، صندوق و کاپوت (First release: open door / trunk / hood alert banner)
- تنظیم یک‌باره که خودش سیگنال هر در رو از CANBUS یاد می‌گیره (One-time wizard that learns each door's CANBUS signal)
- نمایش آزمایشی و صفحه‌ی داده‌های خام CANBUS (Demo alert and raw CANBUS data view)
