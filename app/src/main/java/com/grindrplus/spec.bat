@echo off
setlocal EnableDelayedExpansion

set "OUTPUT=S401.txt"
if exist "%OUTPUT%" del "%OUTPUT%"
echo. > "%OUTPUT%"
set "TOTAL=0"


for /f "delims=" %%X in (

"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\FeatureManager.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\Hook.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\HookAdapter.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\Hooker.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\HookManager.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\MediaUtils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\PCHIP.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\RetrofitUtils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\SuspendResultUtils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\Task.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\utils\TaskManager.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\ui\Utils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\tasks\AlwaysOnline.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\AlbumContentEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\AlbumEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfileEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfilePhotoEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfileViewEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\SavedPhraseEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\TeleportLocationEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\mappers\AlbumMapper.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\AlbumDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfileDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfilePhotoDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfileViewDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\SavedPhraseDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\TeleportLocationDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\converters\DateConverter.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\GPDatabase.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\converters\DateConverter.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\AlbumDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfileDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfilePhotoDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\ProfileViewDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\SavedPhraseDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\dao\TeleportLocationDao.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\mappers\AlbumMapper.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\AlbumContentEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\AlbumEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfileEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfilePhotoEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\ProfileViewEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\SavedPhraseEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\persistence\model\TeleportLocationEntity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\AllowScreenshots.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\AntiBlock.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\AntiDetection.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\BanManagement.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ChatIndicators.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ChatTerminal.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\DisableAnalytics.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\DisableBoosting.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\DisableShuffle.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\DisableUpdates.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\EmptyCalls.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\EnableUnlimited.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ExpiringMedia.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\Favorites.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\FeatureGranting.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\LocalSavedPhrases.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\LocationSpoofer.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\NotificationAlerts.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\OnlineIndicator.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ProfileDetails.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ProfileViews.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ProfileViewsTracker.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\QuickBlock.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\ReverseRadarTabs.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\SignatureSpoofer.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\SSLUnpinning.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\StatusDialog.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\TimberLogging.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\UnlimitedAlbums.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\UnlimitedProfiles.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\hooks\WebSocketAlive.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http\Client.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http\Interceptor.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\Config.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\constants"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\Constants.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\CoroutineHelper.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\DatabaseHelper.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\EventManager.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\InstanceManager.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\Logger.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\TaskScheduler.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\Utils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\constants\GrindrApiError.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http\Client.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\core\http\Interceptor.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Command.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\CommandHandler.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\CommandModule.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Database.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Filtering.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Location.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Profile.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\commands\Utils.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\bridge\BridgeClient.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\bridge\BridgeService.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\bridge\ForceStartActivity.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\bridge\NotificationActionReceiver.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\XposedLoader.kt"
"D:\ANDROID\PROJECTS_GRINDR\4.5-25.12\ReUse140963\app\src\main\java\com\grindrplus\GrindrPlus.kt"
) do (
    if /i "%%~xX"==".kt" if exist "%%X" (
     set /A TOTAL+=1
     echo --- File: %%X --- >> "%OUTPUT%"
     type "%%X" >> "%OUTPUT%" 2>nul
     if errorlevel 1 (
     echo [ERROR] Could not read: %%X >> "%OUTPUT%"
     )
     echo. >> "%OUTPUT%"
     ) else (
     echo [WARN] Skipping non-.kt or missing: %%X
     )
)

rem =============================================================
rem DONE – KEEP WINDOW OPEN
rem =============================================================
echo.
echo =========================================================
echo Finished!  %TOTAL% Kotlin file(s) written to %OUTPUT%
echo =========================================================
echo.
echo Press any key to close this window...
pause >nul