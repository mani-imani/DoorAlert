<div dir="rtl">

# DoorAlert

**هشدار باز موندن در، صندوق و کاپوت روی مانیتور اندرویدی ماشین**

[![Build](https://github.com/mani-imani/DoorAlert/actions/workflows/build.yml/badge.svg)](https://github.com/mani-imani/DoorAlert/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/mani-imani/DoorAlert?label=release)](https://github.com/mani-imani/DoorAlert/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-black.svg)](LICENSE)

[English README](README.en.md)

![DoorAlert](docs/alert-1.png)

اگه یه در، صندوق یا کاپوت باز بمونه، یه کارت بزرگ قرمز و درخشان وسط صفحه ظاهر می‌شه و می‌گه کجا بازه. کارت روی هر اپی که باز باشه نمایش داده می‌شه (مسیریاب، موزیک و هر چیز دیگه) و جلوی کار با اون اپ رو نمی‌گیره. وقتی همه‌چیز بسته شد، کارت سبز می‌شه و خودش می‌ره.

| | |
|:---:|:---:|
| ![](docs/alert-2.png) | ![](docs/alert-ok.png) |

## ویژگی‌ها

- کارت بزرگ قرمز با هاله‌ی نورانی که نبض می‌زنه، متن درشت و علامت هشدار؛ از دور هم دیده می‌شه
- روی هر اپی نمایش داده می‌شه و صفحه‌ی زیرش قابل استفاده می‌مونه
- با زدن روی کارت بسته می‌شه، و تا وقتی یه در دیگه باز نشه دوباره نمیاد
- با روشن شدن مانیتور خودش شروع به کار می‌کنه
- **تنظیم یک‌باره:** اپ خودش یاد می‌گیره سیگنال هر در کدومه
- نمایش آزمایشی و صفحه‌ی داده‌های خام CANBUS برای عیب‌یابی

## سازگاری

| | |
|---|---|
| **ماشین** | جک S3 (JAC S3) |
| **مانیتور** | ZQ8003 · برد `ZQ_MTK_8007` · اندروید ۹، با جعبه‌ی CANBUS |

لازمه مانیتور یه جعبه‌ی CANBUS داشته باشه که اطلاعات ماشین رو بخونه، و سیستمش سرویس `car` (`android.car.server`) رو داشته باشه.

## نصب و راه‌اندازی

1. فایل `DoorAlert.apk` رو از [آخرین Release](https://github.com/mani-imani/DoorAlert/releases/latest) دانلود و نصب کن.
2. سوییچ ماشین رو باز کن و DoorAlert رو اجرا کن. راهنمای تنظیم خودش باز می‌شه:
   - همه‌ی درها رو ببند و «شروع» رو بزن.
   - به ترتیبی که اپ می‌گه، هر بار **فقط یه در** رو باز کن و «ثبت» رو بزن، بعد ببندش.
   - اگه ماشین برای موردی سنسور نداره (مثلاً کاپوت)، «رد کن» رو بزن.
3. تموم. با «نمایش آزمایشی» هم می‌تونی کارت هشدار رو ببینی.

اگه توی تنظیم «تغییری دیده نشد»، توی «داده‌های خام» نگاه کن. اگه با باز و بسته کردن در هیچ خطی آبی نشد، یعنی جعبه‌ی CANBUS وضعیت درها رو نمی‌فرسته.

## چطور کار می‌کنه

- **داده‌ها:** جعبه‌ی CANBUS اطلاعات ماشین رو به سرویس سیستمی `car` مانیتور می‌فرسته، با کلیدهایی مثل `canbus.state.door_data`. DoorAlert هر ۲۵۰ میلی‌ثانیه همه‌ی کلیدهای `canbus.*` رو با یه درخواست (`request.dm.get_value_by_prefix`) می‌خونه و جواب رو مستقیم از Parcel باز می‌کنه ([`CarData.java`](app/src/main/java/ir/maniimani/dooralert/CarData.java)).
- **یادگیری:** همه‌ی مقدارها بیت‌به‌بیت باز می‌شن. اپ اول حالت «همه بسته» رو چند بار نمونه‌برداری می‌کنه تا بیت‌هایی که خودشون تغییر می‌کنن (نویز) رو کنار بذاره. بعد برای هر در، بیتی رو انتخاب می‌کنه که فقط با باز شدن همون در، ثابت و قطعی عوض می‌شه ([`Doors.java`](app/src/main/java/ir/maniimani/dooralert/Doors.java)).
- **کارت هشدار:** یه پنجره‌ی کوچیک وسط صفحه که لمس‌های بیرون از خودش رو به اپ زیرش می‌فرسته ([`PopupActivity.java`](app/src/main/java/ir/maniimani/dooralert/PopupActivity.java)).

## ساخت از سورس

با هر push به `main` بیلد می‌شه. اگه `versionName` بالا بره، Release هم خودکار ساخته می‌شه.

## ساخته‌شده با Claude

این پروژه با کمک **[Claude](https://claude.ai)** ساخته شد، دستیار هوش مصنوعی شرکت Anthropic، روی اشتراک **Claude Pro**. Claude توی همه‌ی مراحل نقش داشت: پیدا کردن داده‌های CANBUS توی کد سیستم مانیتور، طراحی روش یادگیری خودکار، طراحی کارت هشدار و نوشتن کد. تست روی ماشین واقعی و تصمیم‌های طراحی هم کار خودم بود.

## سازنده

**مانی ایمانی‌سنجر** · [GeekMani](https://t.me/geekmani)
[maniimani.ir](https://maniimani.ir) · [GitHub](https://github.com/mani-imani) · [LinkedIn](https://linkedin.com/in/mani-imani)

## مجوز

کد با مجوز [MIT](LICENSE) منتشر شده. فونت [وزیرمتن](https://github.com/rastikerdar/vazirmatn) با مجوز SIL OFL 1.1 همراه اپ قرار گرفته. این پروژه مستقله و هیچ ارتباطی با سازنده‌ی مانیتور یا خودروسازی جک نداره. عکس‌های این صفحه پیش‌نمایش رابط کاربری‌ان.

</div>
