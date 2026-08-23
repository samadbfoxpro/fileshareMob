package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Chapter data class
data class GuideChapter(
    val id: Int,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val iconColor: Color,
    val content: List<GuideParagraph>
)

sealed class GuideParagraph {
    data class Text(val text: String, val isBold: Boolean = false) : GuideParagraph()
    data class Bullet(val text: String) : GuideParagraph()
    data class NoteBox(val text: String, val title: String = "نکته مهم") : GuideParagraph()
    data class WarningBox(val text: String, val title: String = "هشدار امنیتی") : GuideParagraph()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // High quality content chapters
    val chapters = remember {
        listOf(
            GuideChapter(
                id = 1,
                title = "۱. معرفی سیستم و معماری آفلاین",
                summary = "آشنایی با نحوه کارکرد سیستم انتقال فایل مستقیم و بدون اینترنت",
                icon = Icons.Default.Home,
                iconColor = Color(0xFF00A884),
                content = listOf(
                    GuideParagraph.Text("نرم‌افزار FileShare یک ابزار انتقال فایل و پیام‌رسانی فوق‌سریع در بستر شبکه محلی (LAN) است که با تکیه بر معماری نظیربه‌نظیر (Peer-to-Peer) طراحی شده است."),
                    GuideParagraph.Text("ویژگی‌های کلیدی معماری سیستم:", isBold = true),
                    GuideParagraph.Bullet("عدم نیاز به اینترنت: این برنامه ۱۰۰٪ به صورت آفلاین کار می‌کند. برای ارسال فایل یا پیام، نیازی به مصرف ترافیک اینترنت یا اتصال به دنیای خارج ندارید."),
                    GuideParagraph.Bullet("امنیت بیومتریک داده‌ها: تمامی داده‌ها، فایل‌ها و پیام‌های شما مستقیماً بین دستگاه مبدا و مقصد جابجا می‌شوند و هیچ سرور واسط یا ابری در این میان وجود ندارد."),
                    GuideParagraph.Bullet("سرعت انتقال بی‌نظیر: با استفاده از پهنای باند وای‌فای محلی، سرعت انتقال فایل‌ها چندین برابر بلوتوث و اینترنت‌های موبایل است (بسته به قدرت مودم یا هات‌اسپات، تا سرعت ۵۰ مگابایت بر ثانیه)."),
                    GuideParagraph.NoteBox(
                        title = "آیا مصرف اینترنت داریم؟",
                        text = "خیر، فرآیند انتقال به طور کامل در شبکه داخلی خانه، محل کار یا دانشگاه شما انجام می‌شود و حتی اگر اتصال اینترنت مودم شما قطع باشد، برنامه به درستی کار خواهد کرد."
                    )
                )
            ),
            GuideChapter(
                id = 2,
                title = "۲. پیش‌نیازها و راه‌اندازی شبکه",
                summary = "چگونگی اتصال دستگاه‌ها به شبکه محلی مشترک و راه‌اندازی سرور مبدا",
                icon = Icons.Default.Refresh,
                iconColor = Color(0xFF34B7F1),
                content = listOf(
                    GuideParagraph.Text("برای برقرار شدن ارتباط بین فرستنده و گیرنده، رعایت پیش‌نیازهای زیر الزامی است:"),
                    GuideParagraph.Bullet("اتصال به شبکه مشترک: هر دو دستگاه حتماً باید به یک شبکه وای‌فای (Wi-Fi) یا هات‌اسپات (نقطه اتصال تلفن همراه) متصل باشند."),
                    GuideParagraph.Bullet("دیوار آتش و پروکسی: مطمئن شوید فیلترشکن (VPN) بر روی هر دو دستگاه کاملاً خاموش باشد. فیلترشکن‌ها ترافیک محلی را منحرف کرده و مانع شناسایی دستگاه همکار می‌شوند."),
                    GuideParagraph.Bullet("شروع به کار سرور: در دستگاه اصلی، دکمه «شروع سرور» را لمس کنید تا سرویس پس‌زمینه و سرور وب فعال شود. در این حالت آدرس اتصال (مثلا http://192.168.1.5:8080) نمایش داده خواهد شد."),
                    GuideParagraph.WarningBox(
                        title = "توصیه برای انتقال پرسرعت بدون مودم",
                        text = "اگر مودم وای‌فای باکیفیت در دسترس ندارید، کافیست نقطه اتصال (Hotspot) یکی از گوشی‌ها را روشن کرده و گوشی دیگر را به آن وای‌فای متصل کنید. این روش پایدارترین و سریع‌ترین راه اتصال آفلاین است."
                    )
                )
            ),
            GuideChapter(
                id = 3,
                title = "۳. نحوه ارسال فایل و پوشه اشتراکی",
                summary = "آموزش دقیق ارسال فایل توسط گیرنده و مدیریت پوشه اشتراکی فرستنده",
                icon = Icons.Default.Share,
                iconColor = Color(0xFFECE5DD),
                content = listOf(
                    GuideParagraph.Text("برنامه FileShare دو نوع مخزن و شیوه برای جابجایی فایل‌ها ارائه می‌دهد:"),
                    GuideParagraph.Text("مخزن آپلود محلی (مخصوص دریافت فایل):", isBold = true),
                    GuideParagraph.Bullet("فایل‌هایی که کلاینت‌های متصل از طریق مرورگر یا اپلیکیشن برای شما ارسال می‌کنند، در حافظه داخلی گوشی شما در پوشه مخصوص برنامه ذخیره می‌شوند."),
                    GuideParagraph.Bullet("شما می‌توانید این فایل‌ها را باز کنید، حذف کنید یا به اشتراک بگذارید."),
                    GuideParagraph.Text("پوشه اشتراکی کارت حافظه (مخصوص انتشار فایل):", isBold = true),
                    GuideParagraph.Bullet("می‌توانید با فشردن دکمه «تنظیم پوشه اشتراکی»، یکی از پوشه‌های حافظه گوشی خود را انتخاب کنید. با این کار، محتویات آن پوشه به عنوان لیست فایل‌های اشتراکی برای کلاینت‌های متصل نمایش داده می‌شود تا بتوانند آن‌ها را دانلود کنند."),
                    GuideParagraph.NoteBox(
                        title = "امنیت پوشه اشتراکی",
                        text = "برنامه فقط به پوشه‌ای که شما صراحتاً انتخاب و تایید کرده‌اید دسترسی دارد و سایر بخش‌های حافظه گوشی شما هرگز در شبکه محلی به اشتراک گذاشته نخواهند شد."
                    )
                )
            ),
            GuideChapter(
                id = 4,
                title = "۴. امنیت، حریم خصوصی و تایید همکاران",
                summary = "مکانیسم‌های حفاظتی برنامه برای جلوگیری از دسترسی غیرمجاز در شبکه محلی",
                icon = Icons.Default.Lock,
                iconColor = Color(0xFFD0BCFF),
                content = listOf(
                    GuideParagraph.Text("امنیت و حریم خصوصی، اساسی‌ترین اصل در طراحی FileShare است. برای اطمینان از عدم دسترسی افراد غریبه، مکانیسم‌های زیر تعبیه شده است:"),
                    GuideParagraph.Bullet("مکانیسم تایید همکار (Trusted Peers): هرگاه دستگاه جدیدی بخواهد به سرور شما متصل شود یا پیامی ارسال کند، یک درخواست تایید دسترسی روی صفحه گوشی شما ظاهر می‌شود. تا زمانی که دکمه تایید را نزنید، کلاینت هیچ دسترسی به لیست فایل‌ها یا بخش ارسال پیام نخواهد داشت."),
                    GuideParagraph.Bullet("لیست سفید دستگاه‌های معتمد: پس از تایید یک همکار، مشخصات آن ذخیره می‌شود تا در دفعات بعدی نیازی به تایید مجدد نباشد. شما در هر لحظه می‌توانید با مراجعه به لیست همکاران، دسترسی هر دستگاهی را برای همیشه لغو کنید."),
                    GuideParagraph.WarningBox(
                        title = "استفاده در شبکه‌های عمومی و غریبه",
                        text = "هنگام متصل بودن به شبکه‌های وای‌فای عمومی (مانند کافه‌ها، دانشگاه یا فرودگاه)، از تایید دستگاه‌های ناشناس صراحتاً خودداری کنید تا حریم خصوصی فایل‌های شما به خطر نیفتد."
                    )
                )
            ),
            GuideChapter(
                id = 5,
                title = "۵. قوانین و شرایط استفاده حقوقی",
                summary = "بخش حقوقی، سلب مسئولیت و قوانین شرعی و مدنی حاکم بر استفاده از برنامه",
                icon = Icons.Default.Warning,
                iconColor = Color(0xFFFFB4AB),
                content = listOf(
                    GuideParagraph.Text("استفاده از نرم‌افزار FileShare منوط به پذیرش کامل شرایط و قوانین زیر است:"),
                    GuideParagraph.Bullet("مسئولیت محتوا: مسئولیت شرعی، اخلاقی و قانونی هرگونه فایل، تصویر، موسیقی، ویدیو یا متنی که از طریق این برنامه انتقال می‌یابد، به طور کامل بر عهده شخص کاربر (فرستنده و گیرنده) است و توسعه‌دهنده هیچ مسئولیتی در قبال سوء استفاده‌های احتمالی ندارد."),
                    GuideParagraph.Bullet("رعایت قوانین حق نشر (کپی رایت): کاربران متعهد می‌شوند از انتقال محتوای غیرمجاز یا دارای حق مالکیت معنوی بدون کسب اجازه صریح از مالک اثر خودداری کنند."),
                    GuideParagraph.Bullet("رعایت اخلاق شهروندی دیجیتال: استفاده از این نرم‌افزار برای اهدافی نظیر جاسوسی، ایجاد مزاحمت، ارسال محتوای مخرب، یا تلاش برای نفوذ به حریم خصوصی دیگران در شبکه محلی، نقض قوانین بوده و عواقب قانونی آن مستقیماً متوجه متخلف خواهد بود."),
                    GuideParagraph.NoteBox(
                        title = "شفافیت دسترسی‌ها",
                        text = "دسترسی‌های درخواست شده توسط این برنامه (نظیر دسترسی به شبکه و دسترسی به فایل‌ها) صرفاً برای عملکرد فنی انتقال فایل و پیام‌رسانی بوده و هیچ داده‌ای از دستگاه شما به بیرون ارسال نمی‌شود."
                    )
                )
            ),
            GuideChapter(
                id = 6,
                title = "۶. راهنمای عیب‌یابی و رفع خطاها",
                summary = "حل مشکلات رایج عدم شناسایی یا قطع اتصال هنگام انتقال داده‌ها",
                icon = Icons.Default.Info,
                iconColor = Color(0xFFFFB21A),
                content = listOf(
                    GuideParagraph.Text("اگر در برقراری ارتباط بین دو دستگاه با مشکل مواجه شدید، موارد زیر را گام‌به‌گام بررسی کنید:"),
                    GuideParagraph.Bullet("آیا هر دو دستگاه واقعاً به یک وای‌فای متصل هستند؟ گاهی اوقات یک دستگاه به شبکه اصلی و دیگری به شبکه مهمان متصل است که این امر مانع دیدن یکدیگر می‌شود."),
                    GuideParagraph.Bullet("فعال بودن ویژگی انزوای کلاینت (AP Isolation): برخی مودم‌های وای‌فای به دلایل امنیتی اجازه ارتباط مستقیم کلاینت‌ها با یکدیگر را نمی‌دهند. برای حل این مشکل، کافیست هات‌اسپات یکی از دستگاه‌ها را روشن کرده و دستگاه دیگر را به آن وصل کنید."),
                    GuideParagraph.Bullet("خاموش بودن اینترنت همراه (دیتا): در برخی نسخه‌های اندروید، فعال بودن دیتا همزمان با وای‌فای آفلاین، ترافیک را به سمت شبکه سلولار منحرف می‌کند. توصیه می‌شود موقتاً دیتای موبایل را خاموش کنید."),
                    GuideParagraph.Bullet("بسته‌شدن تصادفی برنامه توسط سیستم‌عامل: در برخی گوشی‌ها به دلیل بهینه‌سازی شدید باتری، سرور پس‌زمینه ممکن است متوقف شود. مطمئن شوید دسترسی باتری برای این برنامه روی حالت بدون محدودیت (Unrestricted) تنظیم شده باشد.")
                )
            )
        )
    }

    // Filter chapters based on search query
    val filteredChapters = remember(searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            chapters
        } else {
            chapters.filter { chapter ->
                chapter.title.contains(searchQuery, ignoreCase = true) ||
                        chapter.summary.contains(searchQuery, ignoreCase = true) ||
                        chapter.content.any {
                            when (it) {
                                is GuideParagraph.Text -> it.text.contains(searchQuery, ignoreCase = true)
                                is GuideParagraph.Bullet -> it.text.contains(searchQuery, ignoreCase = true)
                                is GuideParagraph.NoteBox -> it.text.contains(searchQuery, ignoreCase = true) || it.title.contains(searchQuery, ignoreCase = true)
                                is GuideParagraph.WarningBox -> it.text.contains(searchQuery, ignoreCase = true) || it.title.contains(searchQuery, ignoreCase = true)
                            }
                        }
            }
        }
    }

    // Keep track of expanded chapters by ID
    var expandedChapterId by remember { mutableStateOf<Int?>(1) } // Default expand chapter 1

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("guide_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "راهنمای کامل و شرایط استفاده",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "بخش‌بخش، دقیق و مستند",
                            fontSize = 11.sp,
                            color = Color(0xFF8696A0)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1F2C34)
                )
            )
        },
        containerColor = Color(0xFF121B22)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Beautiful M3 Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو در راهنما و قوانین...", color = Color(0xFF8696A0), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو", tint = Color(0xFF8696A0)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن", tint = Color.White)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1F2C34),
                    unfocusedContainerColor = Color(0xFF1F2C34),
                    focusedBorderColor = Color(0xFF00A884),
                    unfocusedBorderColor = Color(0xFF2A3942),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("guide_search_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredChapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "موردی یافت نشد",
                            tint = Color(0xFFFFB4AB),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "موردی برای جستجوی شما یافت نشد!",
                            color = Color(0xFF8696A0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "کلمات کلیدی دیگری مانند 'اتصال'، 'قوانین' یا 'آپلود' را امتحان کنید.",
                            color = Color(0xFF8696A0),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Interactive Chapter Accordion List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (chapter in filteredChapters) {
                        val isExpanded = expandedChapterId == chapter.id
                        ChapterCard(
                            chapter = chapter,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandedChapterId = if (isExpanded) null else chapter.id
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Polished Bottom Footer Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
                        border = BorderStroke(1.dp, Color(0xFF2A3942)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "تضمین حریم خصوصی",
                                tint = Color(0xFF00A884),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "حریم خصوصی شما کاملاً محفوظ است",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تمام داده‌ها به صورت ۱۰۰٪ محلی جابجا شده و هیچ ارتباطی با دنیای خارج وجود ندارد. با خیال راحت فایل‌ها را مدیریت کنید.",
                                color = Color(0xFF8696A0),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterCard(
    chapter: GuideChapter,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) Color(0xFF1F2C34) else Color(0xFF1F2C34).copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) chapter.iconColor else Color(0xFF2A3942)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("chapter_card_${chapter.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(chapter.iconColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = chapter.icon,
                        contentDescription = chapter.title,
                        tint = chapter.iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = chapter.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isExpanded) {
                        Text(
                            text = chapter.summary,
                            color = Color(0xFF8696A0),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "بستن" else "باز کردن",
                    tint = Color(0xFF8696A0),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded content with rich UI
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = Color(0xFF2A3942), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))

                    for (paragraph in chapter.content) {
                        when (paragraph) {
                            is GuideParagraph.Text -> {
                                Text(
                                    text = paragraph.text,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (paragraph.isBold) FontWeight.Bold else FontWeight.Normal,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is GuideParagraph.Bullet -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        color = chapter.iconColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = paragraph.text,
                                        color = Color(0xFFECE5DD),
                                        fontSize = 12.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.weight(1.0f)
                                    )
                                }
                            }
                            is GuideParagraph.NoteBox -> {
                                NoteBoxView(
                                    title = paragraph.title,
                                    text = paragraph.text,
                                    accentColor = chapter.iconColor
                                )
                            }
                            is GuideParagraph.WarningBox -> {
                                WarningBoxView(
                                    title = paragraph.title,
                                    text = paragraph.text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteBoxView(
    title: String,
    text: String,
    accentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = text,
                    color = Color(0xFF8696A0),
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun WarningBoxView(
    title: String,
    text: String
) {
    val warningColor = Color(0xFFED4C5C)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
        border = BorderStroke(1.dp, warningColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = title,
                tint = warningColor,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = warningColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = text,
                    color = Color(0xFF8696A0),
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
