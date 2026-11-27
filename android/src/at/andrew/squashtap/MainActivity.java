package at.andrew.squashtap;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.content.pm.PackageManager;
import androidx.wear.widget.SwipeDismissFrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.wear.ambient.AmbientLifecycleObserver;
import androidx.wear.ambient.AmbientLifecycleObserverKt;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements ScoreView.Actions,LifecycleOwner {
    private SharedPreferences preferences;
    private WatchLanguage ui;
    String tr(String label){return ui.text(label);}
    private MatchBook book;
    private OpponentDirectory opponents;
    private String filterOpponent;
    private LocalDate filterFrom,filterTo;
    private EditText nameInput;
    private TransferClient.Session transfer;
    private boolean transferBusy;
    private ScoreView counter;
    private MatchRooms rooms;
    WatchNfc nfc;
    private UpdateClient updater;
    private LiveScoreClient live;private TextView liveText;private String livePanelState="";
    private UpdatePolicy latest;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private boolean checking,downloading;
    private String updateStatus="Tap Check for updates to contact the release website.";
    private Dialog panel;
    private TextView updateText;
    private ProgressBar updateBar, updateSpinner;
    private int updatePercent = -1;
    private boolean updatePanel;
    private String panelPage="Scoreboard";
    private MenuLocation location;
    private enum ButtonKind {
        PRIMARY, PRIMARY_LINK, NAVIGATION, SECONDARY, CHOICE, PLAYER_LINK, PLAYER_CHOICE, DELETE, BACK, RESULT_TOOL, TILE
    }
    static final ColorStateList MENU_CONTROL_TEXT=MenuSection.text(0xffeeeeee,0xff898989);
    private static final ColorStateList PRIMARY_TEXT=MenuSection.text(0xff191919,0xff252525);
    private static final ColorStateList FILLED_TEXT=MenuSection.text(Color.WHITE,0xffc7c7c7);
    private static final ColorStateList PRIMARY_FILL=MenuSection.filled(0xffdedede);
    private static final ColorStateList DANGER_FILL=MenuSection.filled(0xffa32c46);
    private static final ColorStateList MENU_CONTROL_FILL=MenuSection.states(Color.TRANSPARENT,0xff272727,Color.TRANSPARENT);
    private static final int MENU_CONTROL_BORDER=0xff838383, MENU_CARD_SURFACE=0xff202020;
    private enum PanelTitle { STANDARD, PLAYER_NAME, CUSTOM }
    private static final int MENU_SURFACE=0xff080d17, ROW_RADIUS_DP=15, ROW_GAP_DP=5;
    private boolean sharedArea,scoreMenu;
    private boolean panelShortcut;
    private Runnable playersReturn=this::showMenu,settingsReturn=this::showMenu,matchControlsReturn=this::showMenu;
    private String matchControlsReturnLabel="Back to Menu";
    private String playersReturnLabel="Back to Menu",settingsReturnLabel="Back to Menu";
    private String newMatchOpponent="";
    private int newMatchBestOf=3,resultsPage;
    private boolean sportMenuFromSelector;
    private String selectedSport="squash",newMatchPartner="",newMatchOther="";
    private MatchFormat newMatchFormat=MatchFormat.squash();
    private int manualEditingIndex=-1;
    private int filterMode=-1,newFirstServer=-1,newFirstReceiver=-1;
    private Runnable newMatchReturn=this::closePanel;
    private Runnable newMatchCreated=this::closePanel;
    private String newMatchCreateLabel;
    private Runnable resultsReturn=this::showMenu;
    private String resultsReturnLabel="Back to Menu";
    private ManualMatchEntry manualMatch;
    private Runnable pastMatchReturn=()->showResults(resultsPage);
    private LocalDate manualDate;
    private Runnable transferReturn=()->showResults(resultsPage);
    private String transferReturnLabel="Back to Results",transferView="results";
    private boolean transferFiltered;private String transferResultId="";
    private boolean transferSnapshotFiltered,transferSnapshotBackup;
    private ScrollView panelScroll;
    private String panelScrollKey;
    private final java.util.LinkedHashMap<String,Integer> scrollPositions=new java.util.LinkedHashMap<>();
    private boolean resumed,ambient;
    private ScoreSpeech speech;
    private final android.os.Handler reviewTimer = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reviewDeadline = this::checkFinishedReview;
    private final LifecycleRegistry lifecycle=new LifecycleRegistry(this);
    private AmbientLifecycleObserver ambientObserver;
    private boolean ambientUnavailable;private String widgetSignature="";
    @Override public Lifecycle getLifecycle(){return lifecycle;}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ui=WatchLanguage.get(this);
        speech = new ScoreSpeech(this, () -> android.widget.Toast.makeText(this,
                tr("Spoken scores need an installed watch voice for the selected language."), android.widget.Toast.LENGTH_LONG).show());
        nfc=new WatchNfc(this);
        preferences=getSharedPreferences("squash",MODE_PRIVATE);live=LiveScoreClient.get(this);live.listener(this::onLiveStatusChanged);
        String savedBook=preferences.getString("match_book",null);
        try{book=savedBook==null?MatchBook.migrateLegacy(UUID.randomUUID().toString(),preferences.getString("score","1|")):MatchBook.decode(savedBook);}
        catch(Exception damaged){
            if(savedBook!=null)preferences.edit().putString("match_book_recovery",savedBook).commit();
            int fallbackBest=preferences.getInt("last_best_of",3);book=new MatchBook(new MatchState(UUID.randomUUID().toString(),fallbackBest==5?5:3));
            preferences.edit().putString("diagnostic","Saved match data could not be read. A recovery copy was retained; do not clear app data.").apply();
        }
        if(savedBook!=null&&savedBook.startsWith("B1:")&&!preferences.contains("match_book_v1_backup"))preferences.edit().putString("match_book_v1_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B2:")&&!preferences.contains("match_book_v2_backup"))preferences.edit().putString("match_book_v2_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B4:")&&!preferences.contains("match_book_v4_backup"))preferences.edit().putString("match_book_v4_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B3:")&&!preferences.contains("match_book_v3_backup"))preferences.edit().putString("match_book_v3_backup",savedBook).commit();
        if(preferences.getString("opponent_book","").startsWith("O1:")&&!preferences.contains("opponent_book_v1_backup"))preferences.edit().putString("opponent_book_v1_backup",preferences.getString("opponent_book","")).commit();
        try{opponents=OpponentDirectory.decode(preferences.getString("opponent_book",null));}catch(Exception damaged){preferences.edit().putString("opponent_book_recovery",preferences.getString("opponent_book","")).putString("diagnostic","Saved opponent data could not be read. A recovery copy was retained.").commit();opponents=new OpponentDirectory();}
        persistScore();counter=new ScoreView(this,book.current(),this);setContentView(counter);rooms=new MatchRooms(this);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        AppDiagnostics.record(this,"Activity create");
        if(!BuildConfig.PLAY_DISTRIBUTION)updater=new UpdateClient(this);
        updateStatus=BuildConfig.PLAY_DISTRIBUTION?"Updates are managed by Google Play.":preferences.getString("update_status",updateStatus);
        selectedSport=preferences.getString("selected_sport",book.current().format().sport);if(!java.util.Arrays.asList(MatchFormat.SPORTS).contains(selectedSport))selectedSport="squash";
        if(sportLocked())selectedSport=book.current().format().sport;
        if(rooms.readOnly()){updateDisplay();if(rooms.finalScore())rooms.menu();return;}
        if(!handleWidgetIntent(getIntent())&&Intent.ACTION_MAIN.equals(getIntent().getAction()))openStart();
    }
    private boolean handleWidgetIntent(Intent intent){
        String route=intent==null?null:intent.getStringExtra("keep_score_widget");if(route==null)return false;
        intent.removeExtra("keep_score_widget");
        if(rooms!=null&&rooms.readOnly()){
            if(rooms.finalScore()||rooms.displayed()==null)rooms.menu();else closePanel();
            return true;
        }
        if(!"match".equals(route)&&sportLocked())showSports();else openStart();return true;
    }
    private void updateAmbientSession(boolean ongoing){
        if(!ongoing){
            AmbientLifecycleObserver previous=ambientObserver;ambientObserver=null;
            if(previous!=null){
                lifecycle.removeObserver(previous);
                if(lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED))previous.onPause(this);
                if(lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED))previous.onStop(this);
                previous.onDestroy(this);
            }
            ambient=false;counter.ambient(false,false);return;
        }
        if(ambientObserver!=null||ambientUnavailable)return;
        try{ambientObserver=AmbientLifecycleObserverKt.AmbientLifecycleObserver(this,new AmbientLifecycleObserver.AmbientLifecycleCallback(){
            @Override public void onEnterAmbient(AmbientLifecycleObserver.AmbientDetails details){
                if(!sessionOngoing())return;
                ambient=true;configureSpeech();closePanel();counter.ambient(true,details.getDeviceHasLowBitAmbient());updateDisplay();AppDiagnostics.record(MainActivity.this,"Enter ambient");
            }
            @Override public void onExitAmbient(){ambient=false;configureSpeech();counter.ambient(false,false);updateDisplay();AppDiagnostics.record(MainActivity.this,"Exit ambient");}
            @Override public void onUpdateAmbient(){if(sessionOngoing())counter.invalidate();}
        });
        lifecycle.addObserver(ambientObserver);
        }catch(RuntimeException|LinkageError unavailable){
            if(ambientObserver!=null)lifecycle.removeObserver(ambientObserver);
            ambientObserver=null;ambientUnavailable=true;AppDiagnostics.record(this,"Ambient support unavailable: "+unavailable.getClass().getSimpleName()+" "+unavailable.getMessage());
        }
    }
    @Override protected void onStart(){super.onStart();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);}
    @Override protected void onResume(){
        super.onResume();resumed=true;checkFinishedReview();configureSpeech();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);updateDisplay();
        if(updatePanel)showUpdates();
        if(rooms!=null)rooms.resume();
        if(panelPage.equals("Live scoreboard"))onLiveStatusChanged(live.status());
        AppDiagnostics.record(this,"Activity resume; ambient="+ambient+" paused="+preferences.getBoolean("match_paused",false));
        requestMatchNotification();
    }
    private void requestMatchNotification(){
        if(sessionOngoing()&&resumed&&Build.VERSION.SDK_INT>=33&&!preferences.getBoolean("asked_match_notification",false)){
            preferences.edit().putBoolean("asked_match_notification",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71);
        }
    }
    @Override protected void onPause(){reviewTimer.removeCallbacks(reviewDeadline);speech.close();if(nfc!=null)nfc.pause();if(rooms!=null)rooms.pause();AppDiagnostics.record(this,"Activity pause; page="+panelPage+" screenFlag="+((getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)+" ambient="+ambient+" finishing="+isFinishing());persistScore();resumed=false;updateDisplay();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);super.onPause();}
    @Override protected void onStop(){lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);AppDiagnostics.record(this,"Activity stop; finishing="+isFinishing()+" changingConfig="+isChangingConfigurations());super.onStop();}
    @Override protected void onDestroy(){reviewTimer.removeCallbacks(reviewDeadline);speech.close();if(nfc!=null)nfc.clear();if(rooms!=null)rooms.destroy();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);AppDiagnostics.record(this,"Activity destroy; finishing="+isFinishing());if(live!=null)live.listener(null);tasks.shutdownNow();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(!handleWidgetIntent(intent)){if(!inNewSetup())openStart();}updateDisplay();AppDiagnostics.record(this,"Returned through match shortcut");}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){super.onRequestPermissionsResult(code,names,grants);if(code==71){AppDiagnostics.record(this,"Notification permission result "+(grants.length>0?grants[0]:-1));updateDisplay();}}
    private boolean inNewSetup(){
        for(MenuLocation step=location;step!=null;step=step.parent)
            if(step.key.equals("new-match-setup")||step.key.equals("home-new-sport"))return true;
        return false;
    }

    private boolean reviewing(){
        return book!=null && (rooms==null||!rooms.readOnly()) && !sportLocked() && !unsavedFinish() && FinishedMatchReview.available(preferences,book);
    }

    private void openStart(){
        if(rooms!=null&&rooms.readOnly()){if(rooms.finalScore())rooms.menu();else closePanel();return;}
        if(sportLocked()){
            selectSport(book.current().format().sport);
            if(needsResume())openMatchControls(this::closePanel,"Back to score");else closePanel();
        }else if(unsavedFinish())showSavePending();
        else if(reviewing()&&!FinishedMatchReview.expired(preferences,book,System.currentTimeMillis())){
            FinishedMatchReview.touch(preferences,book,System.currentTimeMillis());
            MatchResult result=FinishedMatchReview.result(preferences,book);
            selectSport(result.format.sport);sharedArea=false;showFinishedResult(result.id);
        }else{
            if(reviewing())FinishedMatchReview.close(preferences,book);
            showHome();
        }
    }

    private void checkFinishedReview(){
        reviewTimer.removeCallbacks(reviewDeadline);
        if(!resumed||!reviewing())return;
        long now=System.currentTimeMillis();
        if(FinishedMatchReview.expired(preferences,book,now)){
            String reviewedId=FinishedMatchReview.result(preferences,book).id;
            FinishedMatchReview.close(preferences,book);
            updateDisplay();
            if(panel==null||location!=null&&(location.key.equals("result:"+reviewedId)||location.key.equals("current-match")))showHome();
            return;
        }
        long last=FinishedMatchReview.lastUsed(preferences,book);
        if(last<=0){FinishedMatchReview.touch(preferences,book,now);last=now;}
        long remaining=FinishedMatchReview.TIMEOUT-Math.max(0,now-last);
        reviewTimer.postDelayed(reviewDeadline,remaining);
    }

    private void reviewInteraction(){
        if(!resumed||!reviewing())return;
        long now=System.currentTimeMillis();
        if(FinishedMatchReview.expired(preferences,book,now)){checkFinishedReview();return;}
        FinishedMatchReview.touch(preferences,book,now);
        checkFinishedReview();
    }

    @Override public void onUserInteraction(){super.onUserInteraction();reviewInteraction();}

    private void markMatchDone(String id){
        if(!reviewing()||!id.equals(FinishedMatchReview.result(preferences,book).id))return;
        FinishedMatchReview.close(preferences,book);
        reviewTimer.removeCallbacks(reviewDeadline);
        showHome();
    }

    private boolean sessionOngoing(){return (rooms==null||!rooms.readOnly()) && MatchDisplayPolicy.ongoing(book.current(),preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),preferences.getBoolean("match_paused",false));}
    private boolean needsResume(){return !book.current().complete()&&!sessionOngoing()&&(book.current().hasPoints()||preferences.getBoolean("match_paused",false));}
    private void setSession(boolean active,boolean paused){
        SharedPreferences.Editor edit=preferences.edit().putBoolean("match_paused",paused);
        if(active)edit.putString(MatchDisplayPolicy.ACTIVE_MATCH,book.current().id()).putString("match_ready_id",book.current().id());else edit.remove(MatchDisplayPolicy.ACTIVE_MATCH);
        edit.commit();
        AppDiagnostics.record(this,(active?"Match activated":paused?"Match paused":"Match idle")+"; battery "+BatterySnapshot.describe(this));
        if(active)requestMatchNotification();
    }
    private void updateDisplay(){
        if(counter==null||book==null)return;
        boolean paused=preferences.getBoolean("match_paused",false);
        boolean ongoing=sessionOngoing();updateAmbientSession(ongoing);
        boolean awake=MatchDisplayPolicy.keepAwake(book.current(),preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),paused,preferences.getBoolean("match_keep_awake_v3",true),resumed,panel!=null,ambient);
        if(rooms!=null && rooms.readOnly())awake=rooms.displayed()!=null && !rooms.finalScore() && preferences.getBoolean("match_keep_awake_v3",true) && resumed && panel==null && !ambient;
        counter.setKeepScreenOn(awake);counter.session(ongoing,needsResume());counter.ownName(preferences.getString("my_name",""));counter.palette(preferences.getString("score_palette","classic"));counter.opponent(book.current().format().doubles?teamName(book.current().id(),1):opponents.nameFor(book.current().id()));counter.players(opponents.roleName(book.current().id(),1),opponents.roleName(book.current().id(),0),opponents.roleName(book.current().id(),2));
        if(rooms!=null){rooms.localChanged();rooms.decorate(counter);}
        if(live!=null&&live.session()!=null){try{live.offer(liveSnapshot());}catch(Exception ignored){}}
        String remoteRevision=rooms==null?"":rooms.widgetRevision();
        String signature=remoteRevision.isEmpty()
            ?book.current().encode()+"|"+FinishedMatchReview.available(preferences,book)+"|"+ownName()+"|"+ongoing+"|"+paused+"|"+preferences.getString("match_ready_id","")
            :remoteRevision;
        if(!signature.equals(widgetSignature)){widgetSignature=signature;MatchWidgets.update(this);}
        if(awake)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(!resumed&&panel!=null)panel.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent service=new Intent(this,MatchSessionService.class);
        if(!ongoing)stopService(service);
        else if(resumed){try{startForegroundService(service);}catch(Exception error){AppDiagnostics.record(this,"Session start failed: "+safe(error));}}
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void action(int target){
        if(rooms!=null && rooms.readOnly()){rooms.menu();return;}
        switch(target){
            case TouchTargets.BLUE:case TouchTargets.RED:
                if(needsResume()){openMatchControls(this::closePanel,"Back to score");return;}
                if(book.current().format().pickleball()&&book.current().firstServer(book.current().gameCount()-1)<0){showServing();return;}
                MatchState beforePoint=MatchState.decode(book.current().encode());
                if(book.add(target)){
                    MatchHaptics.play(this,preferences.getString("haptic_mode","milestones"),MatchReminders.milestone(beforePoint,book.current()));
                    if(preferences.getBoolean("match_reminders",false))counter.reminder(MatchReminders.transition(beforePoint,book.current()));
                    if(!sessionOngoing()&&!book.current().complete())setSession(true,false);
                    if(!afterMatchChange())return;
                    speakLocalScore(beforePoint, false);
                    if(book.current().complete()){
                        if(book.result(book.current().id())!=null)showFinishedResult(book.current().id());
                        else if(!book.currentDeleted())showSavePending();
                    }
                }break;
            case TouchTargets.UNDO:undoPoint();break;
            case TouchTargets.NEW:
                if(book.nextGame()){afterMatchChange();}else showNewMatch();break;
            case TouchTargets.MENU:sportMenuFromSelector=false;scoreMenu=true;selectSport(book.current().format().sport);showMenu();break;
            default:break;
        }
    }
    private boolean persistScore(){
        if(preferences==null||book==null)return true;
        boolean saved=preferences.edit().putString("match_book",book.encode()).putString("opponent_book",opponents.encode()).commit();
        if(!saved)preferences.edit().putString("diagnostic","Storage did not confirm saving match data. Keep the app open and retry.").apply();
        return saved;
    }
    private void showMenu(){
        if(rooms!=null && rooms.readOnly()){rooms.menu();return;}
        if(!allowSport(selectedSport))return;
        sharedArea=false;
        LinearLayout menu=panel("Menu","sport-menu:"+selectedSport,MenuSection.SPORTS);
        boolean here=book.current().format().sport.equals(selectedSport)&&(hasCurrentMatch()||scoreMenu);
        section(menu,"Match");
        if(!sportLocked() && !unsavedFinish()){
            primaryNavigationButton(menu,here?tr("New match"):newSportMatchLabel(),()->showNewMatch(this::showMenu));
            if(here)navigationButton(menu,tr("Current match"),()->openMatchControls(this::showMenu,"Back to Menu"));
        }else{
            primaryNavigationButton(menu,tr("Current match"),()->openMatchControls(this::showMenu,"Back to Menu"));
        }
        personButton(menu,tr("Players"),()->openPlayers(this::showMenu,"Back to Menu"));
        sharingButton(menu,MatchRooms.Entry.SPORT);
        section(menu,"Match history");
        navigationButton(menu,tr("Results (")+sportResults(-1).size()+")",()->openResults(this::showMenu,"Back to Menu"));
        navigationButton(menu,tr("Statistics"),this::showStatistics);
        section(menu,"Sport");
        navigationButton(menu,tr("Favourite formats"),()->showPresets(false));
        navigationButton(menu,tr("Scoring rules"),this::showSportRules);
        navigationButton(menu,tr("Change sport"),this::showSports);
        section(menu,"App");
        navigationButton(menu,tr("Settings"),()->openSettings(this::showMenu,"Back to Menu"));
        if(here&&!sportMenuFromSelector)backButton(menu,"Back to score",this::closePanel);
        else backButton(menu,"Back to sports",this::showSports);
    }
    private boolean hasCurrentMatch(){if(book.current().complete())return unsavedFinish()||reviewing()&&book.current().id().equals(FinishedMatchReview.result(preferences,book).id);return book.current().hasPoints()||book.currentStartedAt()>0||sessionOngoing()||needsResume()||book.current().id().equals(preferences.getString("match_ready_id",""));}
    private boolean sportLocked(){return !book.current().complete()&&hasCurrentMatch();}
    private boolean unsavedFinish(){return book.current().complete() && !book.currentDeleted() && book.result(book.current().id())==null;}
    private String newSportMatchLabel(){return ui.format("New {0} match","Neue Partie · {0}","Nouveau match · {0}","Nuevo partido · {0}",tr(MatchFormat.name(selectedSport)));}
    private boolean allowSport(String sport){if(rooms!=null&&rooms.readOnly()){rooms.menu();return false;}if(sportLocked()&&!sport.equals(book.current().format().sport)){showSportLocked();return false;}return true;}
    private void showSportLocked(){
        selectedSport=book.current().format().sport;sharedArea=false;LinearLayout content=panel("Match still running",null,MenuSection.CURRENT_MATCH);
        paragraph(content,ui.format("Finish or cancel your {0} match before choosing another sport. Pausing keeps the sport locked.","Beende deine Partie ({0}) oder brich sie ab, um die Sportart zu wechseln. Bei einer Pause bleibt sie gesperrt.","Termine ou annule ton match de {0} pour changer de sport. Une pause conserve le sport actuel.","Termina o cancela tu partido de {0} para cambiar de deporte. Una pausa mantiene el deporte actual.",tr(book.current().format().name())));
        primaryNavigationButton(content,tr("Current match"),()->openMatchControls(this::showSports,"Back to sports"));navigationButton(content,tr("Cancel match"),()->showCancelMatch(this::showSportLocked));backButton(content,"Back to sports",this::showSports);
    }
    private void openMatchControls(Runnable back,String label){
        matchControlsReturn=back;
        matchControlsReturnLabel=label;
        showMatchControls();
    }
    void showMatchControls(){
        if(rooms!=null && rooms.readOnly()){rooms.menu();return;}
        MatchState match=book.current();
        selectSport(match.format().sport);
        sharedArea=false;
        boolean finished=match.complete();
        boolean saved=finished && book.result(match.id())!=null;
        boolean sharingAvailable=!finished || rooms.hasRoom() || hasAudienceLink();
        boolean ongoing=sessionOngoing();
        String status=finished?"Finished":ongoing?"Playing":needsResume()?"Paused / ready to resume":"Ready to play";
        LinearLayout menu=panel("Current match","current-match:"+match.id()+":"+status,MenuSection.CURRENT_MATCH);

        if(!finished){
            String action=ongoing?"Pause match":needsResume()?"Resume match":"Start match";
            primaryButton(menu,tr(action),()->{setSession(!ongoing,ongoing);closePanel();updateDisplay();});
        }else if(unsavedFinish()){
            primaryButton(menu,tr("Save match result"),this::saveCurrentResult);
            navigationButton(menu,tr("New match"),()->showNewMatch(this::showMatchControls));
        }else{
            primaryNavigationButton(menu,tr("New match"),()->showNewMatch(this::showMatchControls));
            if(saved)navigationButton(menu,tr("View result"),()->showResult(match.id(),resultsPage,this::showMatchControls,"Back to Current match"));
            if(reviewing()&&match.id().equals(FinishedMatchReview.result(preferences,book).id))secondaryButton(menu,tr("Mark match as done"),()->markMatchDone(match.id()));
        }

        section(menu,finished?"Match summary":"Score");
        String left=match.format().doubles?teamName(match.id(),0):ownName();
        String right=match.format().doubles?teamName(match.id(),1):opponentName(match.id());
        String tally=ui.periodUnit(match.format(),true)+"  "+match.gamesWon(0)+"–"+match.gamesWon(1);
        if(!finished && !match.nextReady()){
            tally+=" · "+tr("Points")+"  "+tr(match.game().displayPoint(0))+"–"+tr(match.game().displayPoint(1));
        }
        paragraph(menu,tr(status)+tr(" · Best of ")+match.bestOf()+"\n"+left+" – "+right+"\n"+tally);
        if(!finished)navigationButton(menu,tr("Enter set score"),this::enterCurrentSet);
        navigationButton(menu,tr("Match details"),this::showCurrentMatch);

        if(!finished){
            section(menu,"Players & options");
            matchPlayerButton(menu,match.id(),match.format().doubles,this::showMatchControls);
            navigationButton(menu,tr("Match options"),this::showMatchOptions);
        }
        if(saved || sharingAvailable){
            section(menu,"Sharing");
            if(saved)navigationButton(menu,tr("Share result card"),()->openPhone("results",false,this::showMatchControls,"Back to Current match",match.id()));
            if(sharingAvailable)sharingButton(menu,MatchRooms.Entry.MATCH);
        }
        if(!finished){
            section(menu,"End match");
            navigationButton(menu,tr("Save retirement result"),()->showEndMatch(this::showMatchControls));
            navigationButton(menu,tr("Cancel match"),this::showCancelMatch);
        }
        if(matchControlsReturnLabel.equals("Back to score"))panelShortcut=false;
        backButton(menu,matchControlsReturnLabel,matchControlsReturn);
    }
    private void matchPlayerButton(LinearLayout menu,String matchId,boolean doubles,Runnable back){
        if(doubles)personButton(menu,tr("Players"),()->showTeam(matchId,back));
        else personButton(menu,tr("Opponent: ")+opponentName(matchId),()->showOpponentPicker(matchId,false,back));
    }
    private void showMatchOptions(){
        MatchState match=book.current();if(match.complete()){showMatchControls();return;}
        LinearLayout menu=panel("Match options","match-options:"+match.id(),MenuSection.CURRENT_MATCH);
        section(menu,"Setup & players");
        matchPlayerButton(menu,match.id(),match.format().doubles,this::showMatchOptions);
        navigationButton(menu,tr("Serving indicator"),this::showServing);
        navigationButton(menu,tr("Match length: Best of ")+match.bestOf(),this::showMatchFormat);
        paragraph(menu,tr("Scoring: ")+ui.rules(match.format())+tr("\nChoose different scoring rules when creating a new match."));
        section(menu,"Finish or replace");
        navigationButton(menu,tr("New match"),()->showNewMatch(this::showMatchOptions));
        navigationButton(menu,tr("Save retirement result"),()->showEndMatch(this::showMatchOptions));
        paragraph(menu,tr("Retirement saves a win/loss. Cancel match discards this match without a result."));
        navigationButton(menu,tr("Cancel match"),()->showCancelMatch(this::showMatchOptions));
        backButton(menu,"Back to Current match",this::showMatchControls);
    }
    private void showSettings(){
        LinearLayout menu=panel("Settings","settings",MenuSection.SETTINGS);paragraph(menu,"Keep Score v"+BuildConfig.VERSION_NAME);
        section(menu,"Language & display");
        navigationButton(menu,tr("Language")+" · "+ui.locale.getDisplayLanguage(ui.locale),this::chooseLanguage);
        navigationButton(menu,tr("Colour scheme: ")+tr(ScorePalette.get(preferences.getString("score_palette","classic")).name),this::choosePalette);
        navigationButton(menu,tr("Brightness: watch system"),this::showBrightness);
        navigationButton(menu,tr("Keep screen on: ")+(preferences.getBoolean("match_keep_awake_v3",true)?tr("ON"):tr("OFF")),this::chooseScreenPolicy);
        section(menu,"Feedback");
        navigationButton(menu,tr("Vibrations: ")+vibrationLabels()[vibrationIndex()],this::chooseVibrations);
        navigationButton(menu,tr("Announce score")+" · "+tr(spokenScores() ? "On" : "Off"),this::chooseSpokenScores);
        navigationButton(menu,tr("Court and interval reminders: ")+(preferences.getBoolean("match_reminders",false)?tr("On"):tr("Off")),this::chooseReminders);
        section(menu,"Data");navigationButton(menu,tr("Backup & restore"),this::showBackup);
        section(menu,"Watch shortcuts");
        navigationButton(menu,tr("Watch tile & face score"),()->{LinearLayout c=panel("Quick access");paragraph(c,tr("Add the Keep Score tile from your watch’s tile picker. On a compatible watch face, add Keep Score · Match score as a complication. Tapping either opens the match without changing its score or pause state."));backButton(c,"Back to Settings",this::showSettings);});
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            secondaryButton(menu,tr("Enable match shortcut"),()->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71));
        section(menu,"App");
        navigationButton(menu,tr("About me"),this::showAboutMe);
        navigationButton(menu,tr("Check for updates"),()->{showUpdates();checkUpdates();});
        navigationButton(menu,tr("Diagnostics"),this::showDiagnostics);backButton(menu,settingsReturnLabel,settingsReturn);
    }
    private void showAboutMe(){
        LinearLayout content=panel("About me","about-me",MenuSection.SETTINGS);
        panelHeading(content,"Andrew Lindley",true);
        paragraph(content,tr("I play squash and created Keep Score to keep track of rallies on my watch. You can now score six racket sports with it."));
        primaryNavigationButton(content,tr("Imprint & contact"),()->showQr(BuildConfig.SITE_URL+"/imprint?lang="+ui.code,
                "Scan with your phone",this::showAboutMe,"Back to About me"));
        backButton(content,"Back to Settings",this::showSettings);
    }
    void roomSettings(LinearLayout page){navigationButton(page,tr("Settings"),()->openSettings(rooms::menu,"Back to Menu"));}
    private void chooseLanguage(){
        LinearLayout content=panel("Language","language");
        String selected=preferences.getString(WatchLanguage.PREFERENCE,"auto");
        String[] names={tr("Automatic (watch)"),"English","Deutsch","Français","Español"};
        for(int i=0;i<WatchLanguage.CODES.length;i++){
            final String code=WatchLanguage.CODES[i];
            choiceButton(content,names[i],code.equals(selected),()->{
                if(code.equals(selected))return;
                if(!preferences.edit().putString(WatchLanguage.PREFERENCE,code).commit()){
                    message(tr("Setting not saved"),tr("The watch could not save this change."),this::chooseLanguage);
                    return;
                }
                ui=WatchLanguage.get(this);
                configureSpeech();
                counter.language(ui);
                widgetSignature="";
                updateDisplay();
                chooseLanguage();
            });
        }
        backButton(content,"Back to Settings",this::showSettings);
    }
    private String[] vibrationLabels(){return new String[]{tr("Off"),tr("Point confirmation"),tr("Points and milestones")};}
    private int vibrationIndex(){String value=preferences.getString("haptic_mode","milestones");return value.equals("off")?0:value.equals("points")?1:2;}
    private void chooseVibrations(){settingChoices(tr("Vibrations"),tr("Distinct patterns identify points, games, sets and match completion."),vibrationLabels(),vibrationIndex(),v->preferences.edit().putString("haptic_mode",new String[]{"off","points","milestones"}[v]).commit());}
    private boolean spokenScores() {
        return preferences.getBoolean(ScoreSpeech.PREFERENCE, false);
    }
    private void configureSpeech() {
        if (resumed && !ambient && spokenScores()) speech.start(ui.locale);
        else speech.close();
    }
    private void chooseSpokenScores() {
        settingChoices(tr("Announce score"), tr("Hear the score after each rally and Undo. The serving side is called first when known. Uses watch media volume and an installed voice."),
                new String[]{tr("Off"), tr("On")}, spokenScores() ? 1 : 0, value -> {
                    if (!preferences.edit().putBoolean(ScoreSpeech.PREFERENCE, value == 1).commit()) return false;
                    configureSpeech();
                    if (value == 1) speech.say(tr("Spoken scores on."));
                    return true;
                });
    }
    private void speakLocalScore(MatchState before, boolean correction) {
        if (!resumed || ambient || !spokenScores()) return;
        MatchState match = book.current();
        speech.say(SpokenScore.call(ui, before, match, correction, slot -> slot == 0
                ? preferences.getString("my_name", "") : opponents.roleName(match.id(), slot - 1)));
    }
    void speakRoomScore(MatchState before, MatchState after) {
        if (resumed && !ambient && roomScoreVisible() && spokenScores())
            speech.say(SpokenScore.remote(ui, before, after, rooms::recordedName));
    }
    private void chooseReminders(){settingChoices(tr("Match reminders"),tr("Brief non-blocking reminders. Players remain responsible for court changes and breaks."),new String[]{tr("Off"),tr("On")},preferences.getBoolean("match_reminders",false)?1:0,v->preferences.edit().putBoolean("match_reminders",v==1).commit());}
    private void settingChoices(String title,String help,String[] labels,int selected,java.util.function.IntPredicate save){
        LinearLayout c=panel(title,"settings:"+title);
        for(int i=0;i<labels.length;i++){final int value=i;choiceButton(c,labels[i],value==selected,()->{if(value==selected)return;if(save.test(value)){updateDisplay();settingChoices(title,help,labels,value,save);}else message(tr("Setting not saved"),tr("The watch could not save this change."),()->settingChoices(title,help,labels,selected,save));});}
        paragraph(c,help+tr("\n\nChanges save immediately."));backButton(c,"Back to Settings",this::showSettings);
    }
    private void openSettings(Runnable back,String label){sharedArea=true;settingsReturn=back;settingsReturnLabel=label;showSettings();}
    private void openPlayers(Runnable back,String label){sharedArea=true;playersReturn=back;playersReturnLabel=label;showOpponents();}
    private void chooseScreenPolicy(){settingChoices(tr("Screen during play"),tr("Applies only to the visible scoreboard during active play. Menus and paused matches allow normal sleep."),new String[]{tr("Keep screen on"),tr("Allow screen to sleep")},preferences.getBoolean("match_keep_awake_v3",true)?0:1,v->preferences.edit().putBoolean("match_keep_awake_v3",v==0).commit());}
    private void choosePalette(){
        LinearLayout c=panel("Colour scheme","palette");String selected=preferences.getString("score_palette","classic");
        for(String id:ScorePalette.IDS){ScorePalette p=ScorePalette.get(id);choiceButton(c,tr(p.name),id.equals(selected),()->{if(id.equals(selected))return;if(preferences.edit().putString("score_palette",id).commit()){updateDisplay();choosePalette();}else message(tr("Colour not saved"),tr("Try choosing the scheme again."),this::choosePalette);});
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,0,0,dp(8));for(int side=0;side<2;side++){TextView sample=new TextView(this);sample.setText(side==0?tr("Me 9"):tr("Opponent 10"));sample.setTextSize(14);sample.setGravity(Gravity.CENTER);sample.setTextColor(side==0?p.leftInk:p.rightInk);sample.setBackgroundColor(side==0?p.left:p.right);sample.setPadding(dp(4),dp(10),dp(4),dp(10));row.addView(sample,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}row.setContentDescription(tr(p.name)+tr(" preview"));c.addView(row);paragraph(c,tr(p.help));
        }
        paragraph(c,tr("Scoreboard colours apply to every sport. Left stays you or your team. Serving keeps its own badge and outline. Ambient stays dim and monochrome. Changes save immediately."));backButton(c,"Back to Settings",this::showSettings);
    }
    private void showBrightness(){LinearLayout c=panel("Watch brightness");paragraph(c,tr("Keep Score follows your watch’s system brightness. Enable Auto brightness or Adaptive brightness in the watch’s Display settings if available. Sunlight colours increase score contrast; the system controls the screen’s brightness."));primaryButton(c,tr("Open watch display settings"),()->{try{startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS));}catch(android.content.ActivityNotFoundException unavailable){message(tr("Open watch Settings"),tr("On the watch, open Settings → Display → Brightness. Names may vary by watch."),this::showBrightness);}});backButton(c,"Back to Settings",this::showSettings);}
    private void showMatchFormat(){
        MatchState match=book.current();LinearLayout content=panel("Match length");
        paragraph(content,tr("Current: Best of ")+match.bestOf());
        for(int value:match.format().bestOptions()){
            final int choice=value;
            Button option=choiceButton(content,tr("Best of ")+value+tr(" · first to ")+(value/2+1),value==match.bestOf(),()->{
                if(choice==match.bestOf()){showMatchOptions();return;}
                if(book.changeBestOf(choice)){
                    preferences.edit().putInt("last_best_of",choice).apply();
                    if(afterMatchChange())showMatchOptions();
                }
            });
            option.setEnabled(value==match.bestOf()||match.canChangeBestOf(value));
        }
        paragraph(content,tr("Changes keep your points, set scores and Undo history."));
        if(match.complete())paragraph(content,tr("This match is finished. Start a new match to choose a different format."));
        else if(match.format().finalTie&&match.hasPoints())paragraph(content,tr("Match length is locked after play starts when a deciding match tie-break is selected."));
        else if(match.bestOf()==5&&!match.canChangeBestOf(3))paragraph(content,tr("A player has already won two sets. Start a new match to use Best of 3."));
        backButton(content,"Back to Match options",this::showMatchOptions);
    }
    private boolean afterMatchChange(){
        if(book.current().complete()&&(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH)||preferences.getBoolean("match_paused",false)))setSession(false,false);
        boolean saved=persistScore();counter.match(book.current());updateDisplay();
        if(!saved){
            LinearLayout warning=panel("Save failed");
            paragraph(warning,tr("The watch did not confirm saving this change. Keep the app open and free storage before retrying."));
            primaryButton(warning,tr("Retry save"),()->{if(persistScore())closePanel();});
            panelShortcut=false;
            backButton(warning,"Back to score",this::closePanel);
        }
        return saved;
    }
    private void undoPoint(){
        undoPoint(this::closePanel);
    }
    private void undoPoint(Runnable cancel){
        if (book.current().hasRetirementCorrection()) {
            final String before = book.encode();
            LinearLayout content = panel("Undo retirement");
            paragraph(content, tr("Reopening restores the recorded rallies from before the phone correction."));
            primaryButton(content, tr("Undo retirement"), ()->{
                if (!before.equals(book.encode())) { message(tr("Match changed"), tr("Review the match before undoing."), this::showMatchControls); return; }
                undoAndAnnounce();
            });
            backButton(content, tr("Cancel"), cancel);
        } else if(book.current().undoEnteredSet()){
            final String before=book.encode();LinearLayout content=panel("Undo entered set?");paragraph(content,tr("This set was entered as a final score. Its point order was not recorded, so Undo removes the whole entered set. Other sets are kept."));
            primaryButton(content,tr("Undo entered set"),()->{if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the match before undoing."),this::showMatchControls);return;}undoAndAnnounce();});backButton(content,tr("Cancel"),cancel);
        }else undoAndAnnounce();
    }
    private void undoAndAnnounce() {
        if (book.undo() && afterMatchChange()) {
            closePanel();
            speakLocalScore(null, true);
        }
    }
    private LinearLayout manualPanel(String title){return manualPanel(title,null);}
    private LinearLayout manualPanel(String title,String key){LinearLayout content=panel(title,key);panel.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);return content;}
    private void showAddPastMatch(Runnable back){pastMatchReturn=back;choosePastFormat();}
    private void chooseSetWinner(String title,String opponent,int oldMe,int oldOther,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        MatchFormat f=manualMatch==null?book.current().format():manualMatch.format;int index=manualMatch==null?(book.current().nextReady()?book.current().gameCount():book.current().gameCount()-1):manualEditingIndex>=0?manualEditingIndex:manualMatch.size();
        boolean tie=manualMatch==null?book.current().matchTie(index):manualMatch.matchTie(index);
        if(!f.legacy()){chooseGenericScore(title,opponent,f,tie,oldMe,oldOther,apply,back);return;}
        LinearLayout content=manualPanel(title,"set-winner:"+title);paragraph(content,tr("Who won this set?"));
        if(oldMe+oldOther>0)ManualEntryUi.score(content,tr("Score to enter"),opponent,oldMe,oldOther,null);
        int loser=oldMe+oldOther==0?7:Math.min(oldMe,oldOther);
        navigationButton(content,tr("Me won"),()->showSetScoreWheel(title,opponent,0,loser,apply,back));
        navigationButton(content,tr("Opponent won"),()->showSetScoreWheel(title,opponent,1,loser,apply,back));
        backButton(content,tr("Cancel"),back);
    }
    private void showSetScoreWheel(String title,String opponent,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title,"set-points:"+title);paragraph(content,(winner==0?tr("Me"):tr("Opponent"))+tr(" won · choose the losing score"));
        ManualEntryUi.ScoreCard preview=ManualEntryUi.score(content,tr("Final set score"),opponent,0,0,null);
        NumberPicker wheel=ManualEntryUi.wheel(content,tr("Losing score"),0,(ScoreState.MAX_RALLIES-2)/2,initial);
        LinearLayout controls=ManualEntryUi.row(content);Button minus=secondaryButton(controls,"−",()->{}),plus=secondaryButton(controls,"+",()->{});ManualEntryUi.equal(minus,plus);
        Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);preview.scores(winner==0?high:low,winner==0?low:high);minus.setEnabled(low>0);plus.setEnabled(low<wheel.getMaxValue());};
        minus.setContentDescription(tr("Decrease losing score"));plus.setContentDescription(tr("Increase losing score"));minus.setOnClickListener(v->{wheel.setValue(Math.max(0,wheel.getValue()-1));refresh.run();});plus.setOnClickListener(v->{wheel.setValue(Math.min(wheel.getMaxValue(),wheel.getValue()+1));refresh.run();});wheel.setOnValueChangedListener((p,a,b)->refresh.run());refresh.run();
        Runnable useScore=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);apply.accept(winner==0?high:low,winner==0?low:high);};
        if(manualMatch!=null)primaryButton(content,tr("Use set score"),useScore);
        else primaryNavigationButton(content,tr("Review set score"),useScore);
        section(content,"Quick scores · Me–Opponent");
        for(int[] presets:new int[][]{{0,5},{7,9}}){LinearLayout row=ManualEntryUi.row(content);Button[] buttons=new Button[2];for(int i=0;i<2;i++){final int low=presets[i],high=ManualMatchEntry.winningScore(low);buttons[i]=secondaryButton(row,(winner==0?high:low)+"–"+(winner==0?low:high),()->{wheel.setValue(low);refresh.run();});}ManualEntryUi.equal(buttons[0],buttons[1]);}
        navigationButton(content,tr("Change winner"),()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);chooseSetWinner(title,opponent,winner==0?high:low,winner==0?low:high,apply,back);});
        backButton(content,tr("Cancel"),back);
    }
    private void enterCurrentSet(){
        manualMatch=null;MatchState match=book.current();if(match.complete()){showMatchControls();return;}
        final String before=book.encode();int number=match.nextReady()?match.gameCount()+1:match.gameCount();int me=match.nextReady()?0:match.game().blue(),other=match.nextReady()?0:match.game().red();
        chooseSetWinner(tr("Set ")+number+tr(" score"),opponentName(match.id()),me,other,(a,b)->{
            if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the current match before entering a set."),this::showMatchControls);return;}
            LinearLayout content=manualPanel(tr("Save entered set?"));ManualEntryUi.score(content,tr("Set ")+number,opponentName(book.current().id()),a,b,null);
            paragraph(content,(me+other>0?tr("This replaces the current set's ")+me+"–"+other+tr(" score. Other sets are kept. "):"")+tr("Only the final score is recorded; point order and finish time stay unknown."));
            if(book.currentStartedAt()==0)paragraph(content,tr("Saving will use the current date and time as the match start."));
            primaryButton(content,tr("Save set score"),()->{
                if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the match before saving."),this::showMatchControls);return;}
                if(book.enterCompletedSet(a,b)&&afterMatchChange()){
                    if(book.current().complete()){if(book.result(book.current().id())!=null)showFinishedResult(book.current().id());else if(!book.currentDeleted())showSavePending();else showMatchControls();}
                    else showEnteredSetSaved(number,a,b);
                }
            });backButton(content,tr("Cancel"),this::showMatchControls);
        },this::showMatchControls);
    }
    private void showEnteredSetSaved(int number,int me,int other){
        LinearLayout content=manualPanel(tr("Set saved"));ManualEntryUi.score(content,tr("Set ")+number,opponentName(book.current().id()),me,other,null);
        primaryButton(content,tr("Score next set live"),()->{if(book.current().complete()){showMatchControls();return;}if(book.current().nextReady())book.nextGame();setSession(true,false);if(afterMatchChange())closePanel();});
        navigationButton(content,tr("Enter next set score"),this::enterCurrentSet);backButton(content,"Back to Current match",this::showMatchControls);
    }
    private String manualOpponent(ManualMatchEntry draft){OpponentDirectory.Entry e=opponents.get(draft.opponentId());return e==null?tr("Opponent"):e.name;}
    private void showManualMatch(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}
        LinearLayout content=manualPanel(tr("Add past match"));paragraph(content,tr("Add a separate completed match to history."));
        section(content,"Format & date");
        for(int option:draft.format.bestOptions()){
            boolean selected=option==draft.bestOf();
            Button length=choiceButton(content,tr("Best of ")+option,selected,()->{
                if(!selected){draft.chooseFormat(option);showManualMatch();}
            });
            length.setEnabled(selected||draft.canChooseFormat(option));
        }
        paragraph(content,tr(draft.format.name())+" · "+ui.rules(draft.format)+tr(" · Best of ")+draft.bestOf());
        navigationButton(content,tr("Date: ")+ui.date(draft.date()),()->{manualDate=draft.date();showManualDate();});
        if(draft.format.doubles){
            section(content,"Our team");
            personButton(content,tr("Partner: ")+playerName(draft.partnerId),()->pickPlayer(tr("Partner"),draft.partnerId,id->{draft.partnerId=id;showManualMatch();},this::showManualMatch));
        }
        section(content,draft.format.doubles?"Opposing team":"Players · optional");
        personButton(content,tr("Opponent: ")+(draft.opponentId().isEmpty()?tr("No name"):manualOpponent(draft))+tr(" (optional)"),this::showManualOpponent);
        if(draft.format.doubles)personButton(content,tr("Opponent 2: ")+playerName(draft.otherId),()->pickPlayer(tr("Opponent 2"),draft.otherId,id->{draft.otherId=id;showManualMatch();},this::showManualMatch));
        section(content,"Set scores");
        for(int i=0;i<draft.size();i++){final int index=i;ManualEntryUi.score(content,tr("Set ")+(i+1)+tr(" · tap to edit"),manualOpponent(draft),draft.points(i,0),draft.points(i,1),()->editManualSet(draft,index));}
        if(!draft.complete())primaryNavigationButton(content,tr("Add set ")+(draft.size()+1),()->editManualSet(draft,draft.size()));
        else paragraph(content,tr("Match complete. Check the set scores, opponent and date before saving."));
        if(draft.size()>0)deleteButton(content,tr("Remove last set"),()->{draft.removeLast();showManualMatch();});
        paragraph(content,tr("Match times are left as Not recorded. You are entering final set scores, not a rally log."));
        Button save=primaryButton(content,tr("Save match result"),()->saveManualMatch(draft));save.setEnabled(draft.complete());
        backButton(content,tr("Cancel"),()->{manualMatch=null;pastMatchReturn.run();});
    }
    private void editManualSet(ManualMatchEntry draft,int index){
        manualEditingIndex=index;int me=index<draft.size()?draft.points(index,0):0,other=index<draft.size()?draft.points(index,1):0;
        chooseSetWinner(tr("Set ")+(index+1)+tr(" score"),manualOpponent(draft),me,other,(a,b)->{if(manualMatch!=draft)return;try{draft.setScore(index,a,b);showManualMatch();}catch(Exception invalid){message(tr("Check set order"),safe(invalid),this::showManualMatch);}},this::showManualMatch);
    }
    private void showManualOpponent(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}LinearLayout content=panel(tr("Choose opponent"),"manual-opponent",MenuSection.PLAYERS);
        playerChoice(content,tr("No name"),draft.opponentId().isEmpty(),()->{draft.opponentId("");showManualMatch();});
        playerList(content,false,(rows,e)->savedPlayerChoice(rows,e.name,e.id.equals(draft.opponentId()),()->{draft.opponentId(e.id);showManualMatch();}));
        paragraph(content,tr("Names are optional. Add them from Opponents → Edit names on phone."));backButton(content,tr("Cancel"),this::showManualMatch);
    }
    private void showManualDate(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}LinearLayout content=manualPanel(tr("Match date"));paragraph(content,ui.date(manualDate));
        secondaryButton(content,tr("Today"),()->{draft.date(LocalDate.now(),LocalDate.now());showManualMatch();});secondaryButton(content,tr("Yesterday"),()->{draft.date(LocalDate.now().minusDays(1),LocalDate.now());showManualMatch();});
        section(content,"Choose a date");
        navigationButton(content,tr("Day: ")+manualDate.getDayOfMonth(),()->manualDateWheel(tr("Day"),1,manualDate.lengthOfMonth(),manualDate.getDayOfMonth(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),manualDate.getMonthValue(),v)));
        navigationButton(content,tr("Month: ")+manualDate.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,ui.locale),()->manualDateWheel(tr("Month"),1,12,manualDate.getMonthValue(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),v,manualDate.getDayOfMonth())));
        navigationButton(content,tr("Year: ")+manualDate.getYear(),()->manualDateWheel(tr("Year"),1900,LocalDate.now().getYear(),manualDate.getYear(),v->manualDate=ManualMatchEntry.changeDate(manualDate,v,manualDate.getMonthValue(),manualDate.getDayOfMonth())));
        primaryButton(content,tr("Use date"),()->{try{draft.date(manualDate,LocalDate.now());showManualMatch();}catch(Exception invalid){message(tr("Check date"),safe(invalid),this::showManualDate);}});backButton(content,tr("Cancel"),this::showManualMatch);
    }
    private void manualDateWheel(String title,int min,int max,int value,java.util.function.IntConsumer apply){
        LinearLayout content=manualPanel(title);NumberPicker wheel=ManualEntryUi.wheel(content,title,min,max,value);if(title.equals(tr("Month"))){String[] months=new String[12];for(int i=1;i<=12;i++)months[i-1]=java.time.Month.of(i).getDisplayName(java.time.format.TextStyle.SHORT,ui.locale);wheel.setDisplayedValues(months);}
        primaryButton(content,ui.format("Use {0}","{0} übernehmen","Utiliser : {0}","Usar {0}",ui.code.equals("de")?title:title.toLowerCase(ui.locale)),()->{apply.accept(wheel.getValue());showManualDate();});backButton(content,"Cancel",this::showManualDate);
    }
    private void saveManualMatch(ManualMatchEntry draft){
        if(manualMatch!=draft)return;
        try{
            OpponentDirectory.Entry person=opponents.get(draft.opponentId());if(!draft.opponentId().isEmpty()&&(person==null||person.archived))throw new IllegalArgumentException(tr("Choose an available saved opponent or No name"));
            MatchBook next=MatchBook.decode(book.encode());OpponentDirectory names=OpponentDirectory.decode(opponents.encode());MatchResult saved=next.importResult(draft.result(UUID.randomUUID().toString()));names.assignTeam(saved.id,draft.opponentId(),draft.partnerId,draft.otherId);
            if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).commit())throw new IllegalStateException(tr("The watch did not confirm saving. Your current match is kept."));
            book=next;opponents=names;manualMatch=null;counter.match(book.current());updateDisplay();showResult(saved.id,resultsPage);
        }catch(Exception invalid){message(tr("Match not saved"),safe(invalid),this::showManualMatch);}
    }
    private void showEndMatch(Runnable back){
        if(book.current().complete()){showMatchControls();return;}
        LinearLayout content=panel("Retirement result");paragraph(content,tr("Who is quitting? The other player wins by retirement. Scores already played are kept; no extra sets or points are awarded."));
        navigationButton(content,book.current().format().doubles?tr("Our team retires"):tr("I quit"),()->confirmRetirement(0,back));navigationButton(content,book.current().format().doubles?tr("Opposing team retires"):tr("Opponent quit"),()->confirmRetirement(1,back));
        backButton(content,tr("Cancel"),back);
    }
    private void showCancelMatch(){showCancelMatch(this::showMatchControls);}
    private void showCancelMatch(Runnable back){
        if(book.current().complete()){showMatchControls();return;}
        final String before=book.encode(),namesBefore=opponents.encode();
        LinearLayout content=panel("Cancel this match?");
        paragraph(content,tr(book.current().format().name())+" · "+tr(book.current().format().mode())+"\n"+currentGamesText());
        paragraph(content,tr("Discard this match and all its scores? No result, win/loss or statistics entry will be saved. Your saved history and player names are kept."));
        deleteButton(content,tr("Discard match"),()->{
            if(!before.equals(book.encode())||!namesBefore.equals(opponents.encode())){message(tr("Match changed"),tr("Review the match before cancelling it."),this::showMatchControls);return;}
            try{
                MatchBook next=MatchBook.decode(before);OpponentDirectory names=OpponentDirectory.decode(namesBefore);String old=next.current().id();
                next.discardCurrent(UUID.randomUUID().toString());names.forgetMatch(old);
                if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).remove("match_ready_id").putBoolean("match_paused",false).commit())throw new IllegalStateException(tr("The watch did not confirm saving. Your match is kept."));
                if(live!=null)live.discard();book=next;opponents=names;manualMatch=null;restoreLocalScore();showSportAfterDiscard();
            }catch(Exception error){message(tr("Match not cancelled"),safe(error),this::showMatchControls);}
        });
        backButton(content,tr("Keep playing"),back);
    }
    private void confirmRetirement(int player,Runnable back){
        if(book.current().complete()){showMatchControls();return;}
        final String before=book.encode();LinearLayout content=panel("Save retirement?");
        paragraph(content,(player==0?tr("You quit. ")+opponentName(book.current().id())+tr(" wins."):opponentName(book.current().id())+tr(" quits. You win."))+"\n\n"+currentGamesText()+tr("\n\nSave this result as retired? You can undo the retirement from the saved result."));
        primaryButton(content,tr("Save retirement"),()->{
            if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the current match before ending it."),()->showEndMatch(back));return;}
            if(book.retire(player)&&afterMatchChange()){
                if(book.result(book.current().id())!=null)showFinishedResult(book.current().id());else if(!book.currentDeleted())showSavePending();else showMatchControls();
            }
        });backButton(content,tr("Cancel"),()->showEndMatch(back));
    }
    private void quickRematch(){
        MatchState m=book.current();if(!m.complete()){showMatchControls();return;}
        selectSport(m.format().sport);newMatchFormat=m.format();newMatchBestOf=m.bestOf();newMatchOpponent=availablePlayer(opponents.roleId(m.id(),0));newMatchPartner=availablePlayer(opponents.roleId(m.id(),1));newMatchOther=availablePlayer(opponents.roleId(m.id(),2));newFirstServer=m.firstServer(0);newFirstReceiver=m.firstReceiver(0);newMatchReturn=this::showMatchControls;startMatch(newMatchBestOf,newMatchOpponent);
    }
    private String availablePlayer(String id){OpponentDirectory.Entry e=opponents.get(id);return e==null||e.archived?"":id;}
    private MatchPresets presets(){return MatchPresets.decode(preferences.getString("favourite_formats",""));}
    private void showPresets(boolean setup){
        LinearLayout c=panel("Favourite formats",null,MenuSection.FAVOURITES);java.util.List<MatchPresets.Entry> list=presets().list(selectedSport);if(list.isEmpty())paragraph(c,tr("Save a favourite from New match to reuse its scoring rules and match length."));
        for(MatchPresets.Entry e:list)navigationButton(c,e.name+"\n"+ui.rules(e.format)+tr(" · BO")+e.best,()->{if(!allowSport(e.format.sport))return;if(!setup)showNewMatch(this::showMenu);newMatchFormat=e.format;newMatchBestOf=e.best;newFirstServer=e.format.pickleball()?0:-1;newFirstReceiver=e.format.pickleball()?1:-1;if(!e.format.doubles){newMatchPartner=newMatchOther="";}showNewMatchOptions();});
        if(!list.isEmpty())navigationButton(c,tr("Remove a favourite"),()->removePreset(setup));backButton(c,setup?"Back to setup":"Back to Menu",setup?this::showNewMatchOptions:this::showMenu);
    }
    private void removePreset(boolean setup){LinearLayout c=panel("Remove favourite");for(MatchPresets.Entry e:presets().list(selectedSport))deleteButton(c,e.name,()->{MatchPresets p=presets();p.remove(e.id);if(preferences.edit().putString("favourite_formats",p.encode()).commit())showPresets(setup);else message(tr("Save failed"),tr("Favourite was kept."),()->showPresets(setup));});backButton(c,tr("Cancel"),()->showPresets(setup));}
    private void savePreset(){
        LinearLayout c=panel("Save favourite",null,MenuSection.FAVOURITES);EditText input=menuInput(c,tr("Favourite name"),tr("Favourite name"),android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(tr(newMatchFormat.name())+" "+tr(newMatchFormat.mode())+" BO"+newMatchBestOf);
        primaryButton(c,tr("Save favourite"),()->{try{MatchPresets p=presets();p.add(input.getText().toString(),newMatchFormat,newMatchBestOf);if(!preferences.edit().putString("favourite_formats",p.encode()).commit())throw new IllegalStateException(tr("Favourite was not saved"));showNewMatchOptions();}catch(Exception e){input.setError(safe(e));}});backButton(c,tr("Cancel"),this::showNewMatchOptions);
    }
    private void showNewMatch(){
        if(panel==null)selectSport(book.current().format().sport);
        showNewMatch(this::closePanel);
    }
    private void showNewMatch(Runnable back){
        showNewMatch(back,this::closePanel,null);
    }
    private void showNewMatch(Runnable back,Runnable created,String createLabel){
        if(!allowSport(selectedSport))return;
        newMatchCreated=created;
        newMatchCreateLabel=createLabel;
        scrollPositions.remove("new-match-setup");
        newMatchReturn=back;try{newMatchFormat=MatchFormat.decode(preferences.getString("last_format_"+selectedSport,MatchFormat.defaults(selectedSport).encode()));if(!newMatchFormat.sport.equals(selectedSport))newMatchFormat=MatchFormat.defaults(selectedSport);}catch(Exception invalid){newMatchFormat=MatchFormat.defaults(selectedSport);}
        newMatchBestOf=preferences.getInt("last_best_"+selectedSport,selectedSport.equals("squash")?preferences.getInt("last_best_of",book.current().format().sport.equals("squash")?book.current().bestOf():5):3);try{newMatchFormat.validateBest(newMatchBestOf);}catch(Exception invalid){newMatchBestOf=3;}
        newMatchOpponent=newMatchPartner=newMatchOther="";newFirstServer=newMatchFormat.pickleball()?0:-1;newFirstReceiver=newMatchFormat.pickleball()?1:-1;showNewMatchOptions();
    }
    private void showNewMatchOptions(){
        OpponentDirectory.Entry chosen=opponents.get(newMatchOpponent);if(chosen==null||chosen.archived)newMatchOpponent="";
        LinearLayout content=panel("New match","new-match-setup",MenuSection.NEW_MATCH);section(content,"Scoring & format");
        formatControls(content,newMatchFormat,f->{newMatchFormat=f;newFirstServer=f.pickleball()?0:-1;newFirstReceiver=f.pickleball()?1:-1;if(!f.doubles){newMatchPartner=newMatchOther="";}showNewMatchOptions();},this::showNewMatchOptions);
        navigationButton(content,tr("Match length: Best of ")+newMatchBestOf,()->chooseLength(newMatchFormat,newMatchBestOf,value->{newMatchBestOf=value;showNewMatchOptions();},this::showNewMatchOptions));
        paragraph(content,ui.rules(newMatchFormat));
        if(newMatchFormat.doubles){
            section(content,"Our team");
            personButton(content,tr("Partner: ")+playerName(newMatchPartner),()->pickPlayer(tr("Partner"),newMatchPartner,id->{newMatchPartner=id;showNewMatchOptions();},this::showNewMatchOptions));
        }
        section(content,newMatchFormat.doubles?"Opposing team":"Players · optional");
        personButton(content,tr("Opponent: ")+(newMatchOpponent.isEmpty()?tr("No name"):chosen.name)+tr(" (optional)"),()->showOpponentPicker(null,false));
        if(newMatchFormat.doubles)personButton(content,tr("Opponent 2: ")+playerName(newMatchOther),()->pickPlayer(tr("Opponent 2"),newMatchOther,id->{newMatchOther=id;showNewMatchOptions();},this::showNewMatchOptions));
        section(content,newMatchFormat.pickleball()?"Starting servers":"Serving · optional");
        navigationButton(content,tr("First server: ")+slotName(newFirstServer,null),()->chooseServer(newMatchFormat,null,newFirstServer,slot->{if(slot!=newFirstServer){newFirstServer=slot;newFirstReceiver=newMatchFormat.pickleball()?1-slot%2:-1;}showNewMatchOptions();},this::showNewMatchOptions));
        if(newMatchFormat.doubles&&newFirstServer>=0)navigationButton(content,(newMatchFormat.nested()||newMatchFormat.pickleball()?tr("Next opposing server: "):tr("First receiver: "))+slotName(newFirstReceiver,null),()->chooseReceiver(newMatchFormat,null,newFirstServer,newFirstReceiver,slot->{newFirstReceiver=slot;showNewMatchOptions();},this::showNewMatchOptions));
        primaryButton(content,newMatchCreateLabel==null?tr("Create match"):newMatchCreateLabel,()->confirmNewMatch(newMatchBestOf));
        paragraph(content,tr("Tap the first point to start play and timing."));
        section(content,"Favourite formats");
        navigationButton(content,tr("Use favourite format"),()->showPresets(true));
        navigationButton(content,tr("Save as favourite"),this::savePreset);
        backButton(content,tr("Cancel"),newMatchReturn);
    }
    private void confirmNewMatch(int bestOf){
        if(!allowSport(newMatchFormat.sport))return;
        final String selected=newMatchOpponent;
        if((hasCurrentMatch()||book.current().hasPoints())&&(!book.current().complete()||book.result(book.current().id())==null&&!book.currentDeleted())){
            LinearLayout confirm=panel("Replace this match?");paragraph(confirm,tr("The current match will be cleared. Saved results stay in history."));
            OpponentDirectory.Entry entry=opponents.get(selected);paragraph(confirm,tr("Opponent: ")+(entry==null?tr("No name"):entry.name));
            deleteButton(confirm,tr("Replace & create match"),()->startMatch(bestOf,selected,newMatchCreated));backButton(confirm,"Back to setup",this::showNewMatchOptions);
        }else startMatch(bestOf,selected,newMatchCreated);
    }
    private void startMatch(int bestOf,String opponentId){
        startMatch(bestOf,opponentId,this::closePanel);
    }
    private void startMatch(int bestOf,String opponentId,Runnable created){
        if(!allowSport(newMatchFormat.sport))return;
        MatchBook next;OpponentDirectory names;
        try{
            OpponentDirectory.Entry selected=opponents.get(opponentId);if(!opponentId.isEmpty()&&(selected==null||selected.archived))throw new IllegalArgumentException(tr("Choose a saved opponent or No name"));
            next=MatchBook.decode(book.encode());names=OpponentDirectory.decode(opponents.encode());next.newMatch(UUID.randomUUID().toString(),bestOf,newMatchFormat);names.assignTeam(next.current().id(),opponentId,newMatchPartner,newMatchOther);next.current().serving(newFirstServer,newFirstReceiver);
        }catch(Exception error){message(tr("Match not started"),safe(error),this::showNewMatchOptions);return;}
        if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).putString("match_ready_id",next.current().id()).putInt("last_best_of",newMatchFormat.sport.equals("squash")?bestOf:preferences.getInt("last_best_of",3)).putInt("last_best_"+newMatchFormat.sport,bestOf).putString("last_format_"+newMatchFormat.sport,newMatchFormat.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).putBoolean("match_paused",false).commit()){
            message(tr("Match not created"),tr("The watch did not confirm saving. Your previous match is kept."),this::showNewMatchOptions);return;
        }
        book=next;opponents=names;counter.match(book.current());updateDisplay();
        newMatchCreated=this::closePanel;newMatchCreateLabel=null;
        created.run();
    }
    private String currentGamesText(){
        MatchState match=book.current();StringBuilder text=new StringBuilder();
        for(int i=0;i<match.gameCount();i++){
            if(i>0)text.append('\n');ScoreState game=match.game(i);
            text.append(tr("Set ")).append(i+1).append(": ").append(game.blue()).append("–").append(game.red());
            if(game.entered())text.append(tr(" · entered score"));
            if(game.winner()==-1)text.append(match.retired()?tr(" · unfinished"):tr(" · playing"));
        }
        return text.toString();
    }
    private void showCurrentMatch(){
        MatchState match=book.current();LinearLayout content=panel("Match details",null,MenuSection.CURRENT_MATCH);
        showSetScores(content,match,match.format().doubles?teamName(match.id(),0):ownName(),match.format().doubles?teamName(match.id(),1):opponentName(match.id()));
        paragraph(content,ui.rules(match.format()));
        paragraph(content,tr("Started")+"\n"+ui.scoreTerms(book.currentStartedText()));
        if(match.retired())paragraph(content,match.retiredPlayer()==0?tr("Me retired · Opponent won"):tr("Opponent retired · You won"));
        if(match.nextReady())primaryButton(content,tr("Next set"),()->{book.nextGame();if(afterMatchChange())closePanel();});
        MatchResult result=book.result(match.id());if(result!=null)navigationButton(content,tr("View result"),()->showResult(result.id,resultsPage,this::showCurrentMatch,"Back to Match details"));
        if(match.complete()&&result==null&&!book.currentDeleted())primaryButton(content,tr("Save match result"),this::saveCurrentResult);
        if(match.complete()&&book.currentDeleted())paragraph(content,tr("This result was deleted."));
        backButton(content,"Back to Current match",this::showMatchControls);
    }
    void showSharedMatchDetails(){
        MatchState match=rooms.displayed();
        if(match==null){rooms.menu();return;}
        LinearLayout page=roomPanel(tr("Match details"),"room-details");
        showSetScores(page,match,rooms.team(0),rooms.team(1));
        backButton(page,"Back to Menu",rooms::menu);
    }
    boolean roomDetailsVisible(){return panel!=null&&"room-details".equals(panelScrollKey);}
    private void showSetScores(LinearLayout page,MatchState match,String left,String right){
        boolean sets=match.format().nested();
        String unit=ui.periodUnit(match.format(),false);
        String won=ui.pick(sets?"Sets won":"Games won","Gewonnene Sätze",sets?"Sets gagnés":"Manches gagnées",sets?"Sets ganados":"Juegos ganados");
        paragraph(page,won+" · "+tr("Best of ")+match.bestOf());
        TextView tally=paragraph(page,match.gamesWon(0)+"  –  "+match.gamesWon(1));
        tally.setTextSize(30);tally.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);tally.setTextColor(Color.WHITE);
        paragraph(page,left+"  /  "+right);
        for(int i=match.gameCount()-1;i>=0;i--){
            ScoreState game=match.game(i);
            String label=unit+" "+(i+1);
            if(match.matchTie(i))label+=" · "+tr("Match tie-break");
            if(game.entered())label+=tr(" · entered score");
            if(game.winner()<0)label+=match.retired()?tr(" · unfinished"):
                (i==match.gameCount()-1&&!match.complete()?" · "+ui.pick("Current","Aktuell","En cours","Actual"):"");
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(6),dp(8),dp(6));row.setBackgroundColor(i%2==0?0xff172436:0xff101b2a);
            TextView name=new TextView(this);name.setText(label);name.setTextSize(14);name.setTextColor(0xffdae6f5);
            row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            TextView score=new TextView(this);score.setText(game.blue()+" – "+game.red());score.setTextSize(20);
            score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);score.setTextColor(Color.WHITE);
            score.setPadding(dp(5),0,0,0);row.addView(score);
            page.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
    private void saveCurrentResult(){
        MatchState match=book.current();
        if(!match.complete()||book.currentDeleted()){showMatchControls();return;}
        if(book.saveCompleted(LocalDate.now())){if(afterMatchChange())showFinishedResult(match.id());}
        else showSavePending();
    }
    private void showSavePending(){
        LinearLayout content=panel("Result not saved yet",null,MenuSection.RESULTS);paragraph(content,tr("Result storage is full. Your completed match is kept on the scoreboard. Delete old results, then open Current match and Save match result."));
        primaryNavigationButton(content,tr("Results"),()->openResults(this::showMenu,"Back to Menu"));backButton(content,"Back to Current match",this::showMatchControls);
    }
    private void openResults(Runnable back,String label){
        resultsReturn=back;
        resultsReturnLabel=label;
        showResultsFromTop(0);
    }
    private void showResultsFromTop(int page){
        showResults(page);
        final ScrollView scroll=panelScroll;
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private void showResults(int requestedPage){
        List<MatchResult> items=filteredResults();
        int pages=Math.max(1,(items.size()+9)/10);
        int page=Math.max(0,Math.min(requestedPage,pages-1));
        resultsPage=page;
        LinearLayout content=panel("Results","results:"+selectedSport+":"+filterMode+":"+page+":"+filterOpponent+":"+filterFrom+":"+filterTo,MenuSection.RESULTS);
        boolean filtered=resultFiltersActive();
        int start=page*10, end=Math.min(start+10,items.size());
        if(items.isEmpty()){
            paragraph(content,tr(filtered?"No matches fit these filters.":"No saved matches yet. Finished matches are saved here automatically."));
            if(filtered)secondaryButton(content,tr("Reset all filters"),this::resetResultFilters);
            else primaryNavigationButton(content,tr("Add past match"),()->showAddPastMatch(()->showResults(resultsPage)));
        }else{
            historyCard(content,items.get(start),page);
        }
        resultTools(content,filtered);
        if(!items.isEmpty()){
            if(filtered)secondaryButton(content,tr("Reset all filters"),this::resetResultFilters);
            if(start+1<end)section(content,"More matches");
            for(int i=start+1;i<end;i++)historyCard(content,items.get(i),page);
            paragraph(content,items.size()+tr(" of ")+book.count()+tr(" saved\nPage ")+(page+1)+tr(" of ")+pages);
            if(page>0)secondaryButton(content,tr("Newer results"),()->showResultsFromTop(page-1));
            if(page+1<pages)secondaryButton(content,tr("Older results"),()->showResultsFromTop(page+1));
        }
        Button back=backButton(content,resultsReturnLabel,resultsReturn);
        if(roundMenu()){
            int width=fitMenuButton(back,roundControlWidth());
            reserveRoundEnd(width);
        }
    }
    private void resultTools(LinearLayout content,boolean filtered){
        LinearLayout row=ManualEntryUi.row(content);
        Button filters=styledButton(row,tr("Filters"),this::showResultFilters,ButtonKind.RESULT_TOOL);
        Button options=styledButton(row,tr("Options"),this::showResultOptions,ButtonKind.RESULT_TOOL);
        filters.setContentDescription(tr("Filter results"));
        filters.setSelected(filtered);
        if(filtered)filters.setStateDescription(tr("Filters active"));
        options.setContentDescription(tr("Result options"));
        fitMenuPair(row,filters,options,roundControlWidth(),false);
    }
    private void showResultOptions(){
        LinearLayout content=panel("Result options",null,MenuSection.RESULTS);
        primaryNavigationButton(content,tr("Add past match"),()->showAddPastMatch(this::showResultOptions));
        navigationButton(content,tr("View results on phone"),()->openPhone("results",false,()->showResults(resultsPage),"Back to Results"));
        section(content,"CSV");
        navigationButton(content,tr("Export CSV"),this::showCsvExport);
        navigationButton(content,tr("Import CSV"),()->openPhone("import",false,this::showResultOptions,"Back to Result options"));
        if(!sportResults(-1).isEmpty()){
            section(content,"Delete results");
            navigationButton(content,tr("Delete all results"),()->confirmDeleteAll(resultsPage));
        }
        backButton(content,"Back to Results",()->showResults(resultsPage));
    }
    private void showCsvExport(){
        LinearLayout content=panel("Export CSV");
        paragraph(content,tr("Scan a phone QR, then download the CSV in your browser."));
        navigationButton(content,tr("All results (")+book.count()+")",()->openPhone("results",false,this::showCsvExport,"Back to Export CSV"));
        Button selected=navigationButton(content,tr("Filtered results (")+filteredResults().size()+")",()->openPhone("results",true,this::showCsvExport,"Back to Export CSV"));selected.setEnabled(!filteredResults().isEmpty());
        backButton(content,"Back to Result options",this::showResultOptions);
    }
    private void showStatistics(){
        MatchStatistics totals=MatchStatistics.from(sportResults(filterMode));
        LinearLayout content=panel("Statistics",null,MenuSection.STATISTICS);
        paragraph(content,modeLabel()+tr(". Date/player filters do not apply."));if(!selectedSport.equals("squash")&&!selectedSport.equals("padel"))navigationButton(content,tr("Mode: ")+modeLabel(),()->chooseResultMode(this::showStatistics));
        if(totals.matchesPlayed()==0)paragraph(content,tr("No saved matches yet. Finish a match or import results to see your balance."));
        statisticsCard(content,tr("Matches"),tr("Played: ")+totals.matchesPlayed()+tr("\nWon: ")+totals.matchesWon+tr(" · Lost: ")+totals.matchesLost+tr("\nBalance: ")+signed(totals.matchBalance())+tr("\nWin rate: ")+(totals.matchesPlayed()==0?"—":Math.round(totals.matchWinRate())+"%"));
        statisticsCard(content,MatchFormat.defaults(selectedSport).nested()?tr("Sets"):tr("Games"),tr("Played: ")+totals.setsPlayed()+tr("\nWon: ")+totals.setsWon+tr(" · Lost: ")+totals.setsLost+tr("\nBalance: ")+signed(totals.setBalance()));
        statisticsCard(content,tr("Points"),tr("Played: ")+totals.pointsPlayed()+tr("\nFor: ")+totals.pointsFor+tr(" · Against: ")+totals.pointsAgainst+tr("\nBalance: ")+signed(totals.pointBalance()));
        if(MatchFormat.defaults(selectedSport).nested())statisticsCard(content,tr("Games"),totals.gamesFor+tr(" won · ")+totals.gamesAgainst+tr(" lost"));if(totals.unknownPointMatches>0)paragraph(content,tr("Point totals exclude unknown points in ")+totals.unknownPointMatches+tr(" manually entered matches."));
        navigationButton(content,tr("Results (")+sportResults(filterMode).size()+")",()->openResults(this::showStatistics,"Back to Statistics"));
        backButton(content,"Back to Menu",this::showMenu);
    }
    private static String signed(long value){return value>0?"+"+value:Long.toString(value);}
    private void statisticsCard(LinearLayout content,String title,String text){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(8),dp(10),dp(8));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(16));shape.setColor(MENU_CARD_SURFACE);card.setBackground(shape);
        TextView heading=paragraph(card,title);heading.setTextSize(16);heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);heading.setTextColor(location.section.accent);heading.setAccessibilityHeading(true);
        TextView values=paragraph(card,text);values.setTextSize(14);values.setTextColor(0xffedf4ff);
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);position.bottomMargin=dp(10);content.addView(card,position);
    }
    private void showFinishedResult(String id){
        checkFinishedReview();
        if(!reviewing()){openStart();return;}
        scrollPositions.remove("result:"+id);
        showResult(id,0);
    }
    private void showResult(String id,int page){
        showResult(id,page,()->showResults(page),"Back to Results");
    }
    private void showResult(String id,int page,Runnable back,String backLabel){
        MatchResult result=book.result(id);if(result==null){showResults(page);return;}
        Runnable again=()->showResult(id,page,back,backLabel);
        boolean locallyControlled=rooms==null || !rooms.readOnly();
        boolean canStart=locallyControlled && !sportLocked() && !unsavedFinish();
        String resultTitle=tr("Match result");
        if(!canStart && result.startedAt>0)resultTitle+=" · "+result.heading();
        LinearLayout content=panel(resultTitle,"result:"+id,MenuSection.RESULTS);
        boolean openReview=locallyControlled && reviewing() && id.equals(FinishedMatchReview.result(preferences,book).id);
        boolean current=openReview && id.equals(book.current().id());
        if(canStart){
            paragraph(content,resultOutcome(result)+" · "+result.gamesWon(0)+"–"+result.gamesWon(1)).setPadding(0,0,0,dp(3));
            primaryNavigationButton(content,tr("New match"),()->{if(selectSport(result.format.sport))showNewMatch(again);});
            if(current)secondaryButton(content,tr("Quick rematch"),this::quickRematch);
            if(openReview)secondaryButton(content,tr("Mark match as done"),()->markMatchDone(id));
        }
        paragraph(content,tr(result.format.name())+" · "+ui.rules(result.format));
        resultDetailCard(content,result);paragraph(content,tr("Set scores: ")+ownName()+"–"+opponentName(result.id));
        navigationButton(content,tr("Share result card"),()->openPhone("results",false,again,"Back to Result",id));
        section(content,"Manage result");
        matchPlayerButton(content,id,result.format.doubles,again);
        if(current)secondaryButton(content,result.retired()?tr("Undo retirement"):book.current().undoEnteredSet()?tr("Undo entered set"):tr("Undo last point"),()->undoPoint(again));
        navigationButton(content,tr("Delete this result"),()->confirmDelete(id,page,again));
        backButton(content,backLabel,back);
    }
    private void confirmDelete(String id,int page,Runnable back){
        MatchResult result=book.result(id);if(result==null){showResults(page);return;}
        LinearLayout content=panel("Delete result?");paragraph(content,ui.scoreTerms(result.title())+"\n"+ui.scoreTerms(result.summary())+tr("\n\nThis cannot be undone. Other results and your active match are kept."));
        deleteButton(content,tr("Delete result"),()->{
            String before=book.encode();book.delete(id);
            if(persistScore())showResults(page);else{book=MatchBook.decode(before);showDeleteFailure(page);}
        });backButton(content,tr("Cancel"),back);
    }
    private void confirmDeleteAll(int page){
        LinearLayout content=panel("Delete all results?");paragraph(content,tr("Delete ")+sportResults(-1).size()+" "+tr(MatchFormat.name(selectedSport))+tr(" saved results? This cannot be undone. Your active scoreboard is kept, and match numbers will not be reused."));
        deleteButton(content,tr("Delete all"),()->{
            String before=book.encode();for(MatchResult result:sportResults(-1))book.delete(result.id);
            if(persistScore())showResults(0);else{book=MatchBook.decode(before);showDeleteFailure(page);}
        });backButton(content,tr("Cancel"),this::showResultOptions);
    }
    private void showDeleteFailure(int page){counter.match(book.current());LinearLayout content=panel("Delete was not saved");paragraph(content,tr("The watch could not save the deletion. Your results have been kept. Free storage and retry."));backButton(content,"Back to results",()->showResults(page));}
    private void showHome(){
        if(sportLocked()||unsavedFinish()||rooms!=null&&rooms.readOnly()){openStart();return;}
        sharedArea=true;scoreMenu=false;
        scrollPositions.remove("home");
        LinearLayout content=panel("Home","home",PanelTitle.CUSTOM,MenuSection.HOME);
        LinearLayout heading=locationHeader(content,null,null,false);
        LinearLayout first=ManualEntryUi.row(content);
        Button start=homeTile(first,MenuSection.NEW_MATCH,tr("New match"),this::chooseHomeSport);
        Button results=homeTile(first,MenuSection.RESULTS,tr("Results"),()->{sharedArea=false;openResults(this::showHome,"Back to Home");});
        results.setContentDescription(tr("Results")+" · "+tr(MatchFormat.name(selectedSport)));
        LinearLayout second=ManualEntryUi.row(content);
        Button players=homeTile(second,MenuSection.PLAYERS,tr("Players"),()->openPlayers(this::showHome,"Back to Home"));
        Button sports=homeTile(second,MenuSection.SPORTS,tr("Sports"),this::showSports);
        int width=roundControlWidth();
        if(roundMenu()){
            heading.measure(View.MeasureSpec.makeMeasureSpec(menuWidth(),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            // The two initial tile rows sit between the brand and the lower curve.
            int lastTileEdge=heading.getMeasuredHeight()+dp(2*48+4);
            width=Math.min(menuWidth(),Math.max(width,roundWidthAt(lastTileEdge)));
        }
        for(Button tile:new Button[]{start,results,players,sports})centerHomeAction(tile,3);
        fitMenuPair(first,start,results,width,true);
        fitMenuPair(second,players,sports,width,true);
        Button settings=homeTile(content,MenuSection.SETTINGS,tr("Settings"),()->openSettings(this::showHome,"Back to Home"));
        centerHomeAction(settings,8);
        int settingsWidth=fitMenuButton(settings,roundControlWidth());
        if(roundMenu()&&first.getOrientation()==LinearLayout.HORIZONTAL&&second.getOrientation()==LinearLayout.HORIZONTAL)
            fitHomeSettings(settings,settingsWidth,heading.getMeasuredHeight()+measureMenuRow(first)+measureMenuRow(second));
        reserveRoundEnd(settingsWidth);
        bindBack(this::finish);
    }

    private void centerHomeAction(Button button,int sidePadding){
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesRelative(null,null,null,null);
        button.setCompoundDrawablePadding(0);
        button.setPadding(dp(sidePadding),dp(4),dp(sidePadding),dp(4));
    }

    private int measureMenuRow(LinearLayout row){
        int width=row.getLayoutParams().width;
        row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        return row.getMeasuredHeight();
    }

    private void fitHomeSettings(Button settings,int width,int top){
        int height=getResources().getDisplayMetrics().heightPixels;
        int available=height-roundInsetForWidth(width)-top;
        android.graphics.Paint.FontMetricsInt font=settings.getPaint().getFontMetricsInt();
        int line=font.bottom-font.top;
        if(available>=dp(48)||available<line)return;
        // Fit the visible pill to the lower curve and keep a 48 dp touch target.
        int inset=dp(48)-Math.min(dp(40),available+dp(3));
        int left=settings.getPaddingLeft(),right=settings.getPaddingRight();
        settings.setBackground(new android.graphics.drawable.InsetDrawable(settings.getBackground(),0,0,0,inset));
        settings.setPadding(left,0,right,inset);
    }

    private boolean roundMenu(){return getResources().getConfiguration().isScreenRound();}

    private int menuWidth(){
        return getResources().getDisplayMetrics().widthPixels-panelScroll.getPaddingLeft()-panelScroll.getPaddingRight();
    }

    private int roundControlWidth(){
        if(!roundMenu())return menuWidth();
        return Math.min(menuWidth(),Math.round(getResources().getDisplayMetrics().widthPixels*.73f));
    }

    private double safeScreenRadius(){
        android.util.DisplayMetrics screen=getResources().getDisplayMetrics();
        return Math.min(screen.widthPixels,screen.heightPixels)/2.0-dp(2);
    }

    private int roundWidthAt(int y){
        double radius=safeScreenRadius();
        double offset=y-getResources().getDisplayMetrics().heightPixels/2.0;
        return (int)(2*Math.sqrt(Math.max(0,radius*radius-offset*offset)));
    }

    private int roundInsetForWidth(float width){
        double radius=safeScreenRadius();
        return (int)Math.ceil(getResources().getDisplayMetrics().heightPixels/2.0-
            Math.sqrt(Math.max(0,radius*radius-width*width/4.0)));
    }

    private void reserveRoundEnd(int visibleWidth){
        if(!roundMenu())return;
        panelScroll.setPadding(panelScroll.getPaddingLeft(),panelScroll.getPaddingTop(),panelScroll.getPaddingRight(),
            Math.max(panelScroll.getPaddingBottom(),roundInsetForWidth(visibleWidth)+dp(5)));
    }

    private int buttonLabelWidth(Button button,boolean allowWrap){
        float width=0;
        String label=button.getText().toString();
        for(String line:allowWrap?label.split(" "):new String[]{label})
            width=Math.max(width,button.getPaint().measureText(line));
        return Math.max(dp(48),(int)Math.ceil(width)+button.getCompoundPaddingLeft()+button.getCompoundPaddingRight());
    }

    private int fitMenuButton(Button button,int maximum){
        int width=Math.min(maximum,buttonLabelWidth(button,false));
        LinearLayout.LayoutParams position=(LinearLayout.LayoutParams)button.getLayoutParams();
        position.width=width;
        position.gravity=Gravity.CENTER_HORIZONTAL;
        button.setLayoutParams(position);
        return width;
    }

    private void fitMenuPair(LinearLayout row,Button left,Button right,int width,boolean allowWrap){
        LinearLayout.LayoutParams position=(LinearLayout.LayoutParams)row.getLayoutParams();
        position.width=width;
        position.gravity=Gravity.CENTER_HORIZONTAL;
        row.setLayoutParams(position);
        int required=Math.max(buttonLabelWidth(left,allowWrap),buttonLabelWidth(right,allowWrap));
        if((width-dp(6))/2<required){
            row.setOrientation(LinearLayout.VERTICAL);
            return;
        }
        ManualEntryUi.equal(left,right);
        if(allowWrap){
            for(Button tile:new Button[]{left,right}){
                LinearLayout.LayoutParams tilePosition=(LinearLayout.LayoutParams)tile.getLayoutParams();
                tilePosition.bottomMargin=dp(4);
                tile.setLayoutParams(tilePosition);
            }
        }
    }

    private void chooseHomeSport(){
        if(sportLocked()){openStart();return;}
        sharedArea=true;
        LinearLayout content=panel("New match","home-new-sport",MenuSection.NEW_MATCH);
        for(String sport:ui.sortedSports()){
            Button choice=navigationButton(content,tr(MatchFormat.name(sport)),()->{
                if(!selectSport(sport))return;
                location.title=MatchFormat.name(sport);
                sharedArea=false;
                showNewMatch(this::chooseHomeSport);
            });
            sportButton(choice,sport);
        }
        backButton(content,"Back to Home",this::showHome);
    }

    void showSports(){
        if(rooms!=null && rooms.readOnly()){rooms.menu();return;}
        sharedArea=true;scoreMenu=false;
        LinearLayout content=panel("Choose sport","sports",MenuSection.SPORTS);
        if(hasCurrentMatch()&&!book.current().complete())primaryNavigationButton(content,(needsResume()?tr("Resume "):tr("Current match")+" · ")+tr(book.current().format().name()),()->openMatchControls(this::showSports,"Back to sports"));
        else if(unsavedFinish())primaryButton(content,tr("Save match result"),this::saveCurrentResult);
        else primaryNavigationButton(content,newSportMatchLabel(),()->{sharedArea=false;showNewMatch(this::showSports);});
        if(sportLocked())paragraph(content,ui.format("Finish or cancel your {0} match to change sport. Paused matches stay locked.","Beende deine Partie ({0}) oder brich sie ab, um die Sportart zu wechseln. Bei einer Pause bleibt sie gesperrt.","Termine ou annule ton match de {0} pour changer de sport. Une pause conserve le sport actuel.","Termina o cancela tu partido de {0} para cambiar de deporte. Una pausa mantiene el deporte actual.",tr(book.current().format().name())));
        for(String sport:ui.sortedSports()){Button choice=navigationButton(content,tr(MatchFormat.name(sport)),()->{if(selectSport(sport)){sportMenuFromSelector=true;showMenu();}});sportButton(choice,sport);boolean enabled=!sportLocked()||sport.equals(book.current().format().sport);choice.setEnabled(enabled);choice.setAlpha(enabled?1f:.4f);choice.setContentDescription(tr(MatchFormat.name(sport))+(enabled?tr(", choose sport"):tr(", locked until the current match is finished or cancelled")));}
        personButton(content,tr("Players"),()->openPlayers(this::showSports,"Back to sports"));
        section(content,"Sharing");
        sharingButton(content,MatchRooms.Entry.SPORTS);
        section(content,"App");
        navigationButton(content,tr("Settings"),()->openSettings(this::showSports,"Back to sports"));
        if(location.parent!=null&&location.parent.key.equals("home"))backButton(content,"Back to Home",this::showHome);
        else backButton(content,tr("Close app"),this::finish);
    }
    void showSportAfterDiscard(){
        if(!selectSport(book.current().format().sport))return;
        MenuLocation home=MenuLocation.open(null,"home","Home",MenuSection.HOME);
        location=MenuLocation.open(home,"sports","Choose sport",MenuSection.SPORTS);
        sportMenuFromSelector=true;
        scoreMenu=false;
        showMenu();
    }
    private boolean selectSport(String sport){if(!allowSport(sport))return false;if(!selectedSport.equals(sport)){filterOpponent=null;filterFrom=filterTo=null;filterMode=-1;resultsPage=0;}selectedSport=sport;preferences.edit().putString("selected_sport",sport).apply();return true;}
    private void showSportRules(){
        LinearLayout content=panel(tr(MatchFormat.name(selectedSport))+tr(" rules"),null,MenuSection.RULES);
        String rules;
        switch(selectedSport){
            case "squash":rules=tr("One point per rally. Games to 11, win by two, without a point cap. A match is normally best of 5 games; best of 3 is also permitted.\n\nThe rally winner serves next. A let replays the rally without a score change. A stroke or service fault awards a point to the appropriate player: tap that player's score.\n\nThe indicator shows who serves, not the service box. At hand-out the server chooses a box; while retaining service the boxes alternate. A let retains the same box.\n\nGame Ball / Match Ball is highlighted. Pause is available for interruptions; its duration is included in elapsed match time.\n\nThis is a scorekeeper. Injury allowances, conduct game/match awards and other referee decisions are not automated. Awarded/conceded games cannot be represented by inventing an 11-point score.\n\nWorld Squash singles rules, effective September 2025.");break;
            case "badminton":rules=tr("Singles or doubles. Best of 3 games. Choose 21-point games (win by two, cap 30) or 15-point games (win by two, cap 21).\n\nThe 15-point format applies internationally from 4 January 2027; specified Austrian competitions switch on 1 January 2027. Local competitions may differ. Both presets remain available.\n\nThe rally winner serves. For doubles, choose the initial server and receiver for each game to show the individual server. Otherwise the indicator can show the serving team.\n\nBWF Laws of Badminton 2025 / 2027.");break;
            case "table-tennis":rules=tr("Singles or doubles. Games to 11, win by two, no point cap. Choose best of 3, 5 or 7.\n\nService changes after two points, and after every point from 10–10. In doubles, the previous receiver becomes server; the previous server's partner receives. Receiving order changes at five points in the deciding game.\n\nChoose the initial server and, for doubles, receiver. A let has no score change. The tournament expedite system is not automated.\n\nITTF Laws of Table Tennis 2026.");break;
            case "pickleball":rules=tr("Singles or doubles. Choose Traditional side-out (only the serving team scores), Rally 2026 (every rally scores), or Rally 2025 (game point must be won while serving). Games to 11, 15 or 21, win by two. One game, best of 3 or best of 5; choose your event or club format.\n\nTap the rally winner even when only service changes. Traditional doubles starts 0–0–2; both players serve on later possessions. Rally doubles uses one server per possession. Choose each team’s starting player before scoring; Undo restores scores, server number and court. Both rally versions stay available.\n\nUSA Pickleball Official Rulebooks 2025 and 2026.");break;
            case "padel":rules=tr("Doubles. Points: 0, 15, 30, 40. Sets to 6 games, win by two, tie-break at 6–6 to 7 points, win by two. Best of 3 sets.\n\nChoose Advantage, Golden Point (a deciding point at deuce), or Star Point (two advantage cycles, then a deciding point at the third deuce). Star Point entered FIP circuits in 2026.\n\nOptionally replace the deciding set with a match tie-break to 10, win by two. Choose the first server and next opposing server for service rotation; the pair may choose their order at a new set.\n\nFIP Rules of Padel 2026.");break;
            default:rules=tr("Singles or doubles. Points: 0, 15, 30, 40, advantage. Choose standard advantage or No-Ad (a deciding point at deuce).\n\nSets to 6 games, win by two, tie-break at 6–6 to 7, win by two. Best of 3 or 5 sets. Optionally replace the deciding set with a match tie-break to 10, win by two.\n\nService rotates after each game. In a tie-break the first server serves one point, then servers alternate for two points each. For doubles, choose the first and next opposing server; the pair may choose their order at a new set.\n\nOnly point winners are recorded, without first/second serve attempts.\n\nITF Rules of Tennis 2026.");
        }
        paragraph(content,rules);backButton(content,"Back to Menu",this::showMenu);
    }
    private void formatControls(LinearLayout content,MatchFormat f,java.util.function.Consumer<MatchFormat> apply,Runnable back){
        if(!f.sport.equals("squash")&&!f.sport.equals("padel"))navigationButton(content,tr("Mode: ")+tr(f.mode()),()->selectOption(tr("Match mode"),tr("Choose the number of players."),new String[]{tr("Singles"),tr("Doubles")},f.doubles?1:0,value->apply.accept(f.mode(value==1)),back));
        if(f.sport.equals("badminton"))navigationButton(content,tr("Rules: ")+f.target+tr(" points · cap ")+f.cap(),()->selectOption(tr("Scoring system"),tr("Both rule sets stay available. Choose the format used by your competition."),new String[]{tr("21 points · cap 30"),tr("15 points · cap 21")},f.target==21?0:1,value->apply.accept(f.target(value==0?21:15)),back));
        if(f.pickleball()){
            String[] rules={tr("Traditional side-out"),tr("Rally 2026 · every rally scores"),tr("Rally 2025 · win on serve")};String[] values={"side-out","rally-2026","rally-2025"};int selected=java.util.Arrays.asList(values).indexOf(f.deuce);
            navigationButton(content,tr("Scoring: ")+rules[selected],()->selectOption(tr("Pickleball scoring"),tr("Tap the rally winner, including rallies that only change service. Each match keeps its chosen rules."),rules,selected,value->apply.accept(f.deuce(values[value])),back));
            navigationButton(content,tr("Target: ")+f.target+tr(" points"),()->selectOption(tr("Game points"),tr("Win by two. Choose your event or club format."),new String[]{tr("11 points"),tr("15 points"),tr("21 points")},f.target==11?0:f.target==15?1:2,value->apply.accept(f.target(new int[]{11,15,21}[value])),back));
        }
        if(f.nested()){
            String[] labels=f.sport.equals("padel")?new String[]{tr("Advantage"),tr("Golden Point"),tr("Star Point")}:new String[]{tr("Advantage"),tr("No-Ad")};
            int selected=f.deuce.equals("advantage")?0:f.deuce.equals("no-ad")?1:2;
            navigationButton(content,tr("Deuce: ")+labels[selected],()->selectOption(tr("Deuce scoring"),tr("Advantage: win by two. No-Ad / Golden Point: one deciding point at deuce.")+(f.sport.equals("padel")?tr(" Star Point: two advantage cycles, then a deciding point at the third deuce."):""),labels,selected,value->apply.accept(f.deuce(value==0?"advantage":value==1?"no-ad":"star")),back));
            navigationButton(content,tr("Decider: ")+(f.finalTie?tr("Match tie-break to 10"):tr("Full set")),()->selectOption(tr("Deciding set"),tr("Choose how to finish when both sides reach the final set."),new String[]{tr("Full set"),tr("Match tie-break to 10")},f.finalTie?1:0,value->apply.accept(f.finalTie(value==1)),back));
        }
    }
    private void selectOption(String title,String help,String[] labels,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel(title);
        for(int i=0;i<labels.length;i++){final int value=i;choiceButton(content,labels[i],i==selected,()->{if(value!=selected)apply.accept(value);});}
        paragraph(content,help);
        backButton(content,tr("Cancel"),back);
    }
    private void chooseLength(MatchFormat format,int current,java.util.function.IntConsumer apply,Runnable back){
        int[] values=format.bestOptions();String[] labels=new String[values.length];int selected=-1;
        for(int i=0;i<values.length;i++){labels[i]=tr("Best of ")+values[i]+tr(" · first to ")+(values[i]/2+1);if(values[i]==current)selected=i;}
        selectOption(tr("Match length"),tr("Choose how many ")+(format.nested()?tr("sets"):tr("games"))+tr(" decide the match."),labels,selected,index->apply.accept(values[index]),back);
    }
    private void chooseResultMode(Runnable back){selectOption(tr("Filter match mode"),tr("Show saved results for the selected sport."),new String[]{tr("All modes"),tr("Singles"),tr("Doubles")},filterMode+1,value->{filterMode=value-1;resultsPage=0;back.run();},back);}
    private String playerName(String id){OpponentDirectory.Entry entry=opponents.get(id);return entry==null?tr("No name"):entry.name;}
    private String teamName(String id,int side){return teamName(id,side,true);}
    private String teamName(String id,int side,boolean translate){
        return scoreName(id,side,translate)+" + "+scoreName(id,side==0?2:3,translate);
    }
    private String slotName(int slot,String matchId){return slotName(slot,matchId,true);}
    private String slotName(int slot,String matchId,boolean translate){
        if(slot<0||slot>=4){
            String label=slot<0?"Not set":slot==4?"Our team":"Opposing team";
            return translate?tr(label):label;
        }
        if(matchId!=null||slot==0)return scoreName(matchId,slot,translate);
        OpponentDirectory.Entry chosen=opponents.get(slot==1?newMatchOpponent:slot==2?newMatchPartner:newMatchOther);
        return chosen==null?scoreName(null,slot,translate):chosen.name;
    }
    private void chooseServer(MatchFormat f,String matchId,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel("First server");
        if(!f.pickleball())choiceButton(content,tr("Not set"),selected<0,()->apply.accept(-1));
        for(int slot=0;slot<(f.doubles?4:2);slot++){
            final int choice=slot;
            playerChoice(content,slotName(choice,matchId),choice==selected,()->apply.accept(choice));
        }
        paragraph(content,f.nested()&&f.doubles?tr("Choose the first server for this set. Set the next opposing server in the following option."):tr("Choose the initial server for this game. The indicator follows recorded points; Undo restores it."));
        backButton(content,tr("Cancel"),back);
    }
    private void chooseReceiver(MatchFormat f,String matchId,int server,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel(f.nested()||f.pickleball()?tr("Next opposing server"):tr("First receiver"));
        for(int slot=0;slot<4;slot++)if(server<0||slot%2!=server%2){
            final int choice=slot;
            playerChoice(content,slotName(choice,matchId),choice==selected,()->apply.accept(choice));
        }
        backButton(content,tr("Cancel"),back);
    }
    private void showServing(){
        MatchState match=book.current();int index=match.gameCount()-1;LinearLayout content=panel("Serving indicator","serving:"+match.id(),MenuSection.CURRENT_MATCH);
        paragraph(content,tr("Next serve: ")+slotName(match.server(),match.id())+(match.format().pickleball()?tr("\nServe call: ")+match.game().pickleCall():"")+tr("\nInitial choices apply to ")+tr(match.format().unit()).toLowerCase()+" "+(index+1)+".");
        if(match.format().pickleball()&&match.game().rallies()>0){paragraph(content,tr("Starting servers are fixed once rallies are recorded. Undo to zero to change them."));backButton(content,"Back to Match options",this::showMatchOptions);return;}
        navigationButton(content,tr("First server: ")+slotName(match.firstServer(index),match.id()),()->chooseServer(match.format(),match.id(),match.firstServer(index),slot->{if(slot==match.firstServer(index)){showServing();return;}match.serving(slot,match.format().pickleball()?1-slot%2:-1);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        if(match.format().doubles&&match.firstServer(index)>=0)navigationButton(content,(match.format().nested()||match.format().pickleball()?tr("Next opposing server: "):tr("First receiver: "))+slotName(match.firstReceiver(index),match.id()),()->chooseReceiver(match.format(),match.id(),match.firstServer(index),match.firstReceiver(index),slot->{match.serving(match.firstServer(index),slot);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        backButton(content,"Back to Match options",this::showMatchOptions);
    }
    private void pickPlayer(String title,String selected,java.util.function.Consumer<String> choose,Runnable back){
        LinearLayout content=panel(title,"role-player:"+title,MenuSection.PLAYERS);
        playerChoice(content,tr("No name"),selected.isEmpty(),()->choose.accept(""));
        if(playerList(content,false,(rows,entry)->markChoice(playerButton(rows,entry,ButtonKind.PLAYER_CHOICE,()->choose.accept(entry.id)),entry.id.equals(selected))))section(content,"Manage names");
        navigationButton(content,tr("Add player"),()->showNameEditor(null,null,back,choose));backButton(content,tr("Cancel"),back);
    }
    private void showTeam(String id,Runnable back){
        LinearLayout content=panel("Players",null,MenuSection.PLAYERS);
        Runnable again=()->showTeam(id,back);
        section(content,"Our team");
        paragraph(content,ownName());
        teamPlayer(content,id,1,"Partner",again);
        section(content,"Opposing team");
        teamPlayer(content,id,0,"Opponent 1",again);
        teamPlayer(content,id,2,"Opponent 2",again);
        backButton(content,tr("Back"),back);
    }
    private void teamPlayer(LinearLayout content,String matchId,int slot,String label,Runnable again){
        personButton(content,tr(label)+": "+playerName(opponents.roleId(matchId,slot)),()->pickPlayer(tr("Choose player"),opponents.roleId(matchId,slot),chosen->{
            String before=opponents.encode();
            try{
                String[] team={opponents.roleId(matchId,0),opponents.roleId(matchId,1),opponents.roleId(matchId,2)};
                team[slot]=chosen;
                opponents.assignTeam(matchId,team[0],team[1],team[2]);
                if(!persistScore())throw new IllegalStateException(tr("Save failed"));
                updateDisplay();
                again.run();
            }catch(Exception error){
                opponents=OpponentDirectory.decode(before);
                message(tr("Player not saved"),safe(error),again);
            }
        },again));
    }
    private String sportsText(String id){java.util.Set<String> sports=opponents.sportsFor(id,book.newestFirst());java.util.List<String> names=new java.util.ArrayList<>();for(String sport:ui.sortedSports())if(sports.contains(sport))names.add(tr(MatchFormat.name(sport)));return names.isEmpty()?tr("No saved results yet"):tr("Results: ")+android.text.TextUtils.join(", ",names);}
    private Button playerButton(LinearLayout content,OpponentDirectory.Entry entry,ButtonKind kind,Runnable action){
        java.util.Set<String> sports=opponents.sportsFor(entry.id,book.newestFirst());StringBuilder label=new StringBuilder(entry.name);if(!sports.isEmpty()){label.append('\n');for(String sport:ui.sortedSports())if(sports.contains(sport))label.append("◦ ");}
        android.text.SpannableString text=new android.text.SpannableString(label);int start=entry.name.length()+1;for(String sport:ui.sortedSports())if(sports.contains(sport)){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));text.setSpan(new android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BASELINE),start,start+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);start+=2;}
        Button button=styledButton(content,entry.name,action,kind);button.setText(text);button.setContentDescription(entry.name+". "+sportsText(entry.id));return button;
    }
    private void choosePastFormat(){
        if(selectedSport.equals("squash")){manualMatch=new ManualMatchEntry(LocalDate.now());showManualMatch();return;}
        MatchFormat f;try{f=MatchFormat.decode(preferences.getString("last_format_"+selectedSport,MatchFormat.defaults(selectedSport).encode()));}catch(Exception error){f=MatchFormat.defaults(selectedSport);}showPastFormat(f);
    }
    private void showPastFormat(MatchFormat f){LinearLayout content=panel("Past match rules",null,MenuSection.RESULTS);paragraph(content,tr(f.name())+" · "+ui.rules(f));formatControls(content,f,this::showPastFormat,()->showPastFormat(f));primaryNavigationButton(content,tr("Continue"),()->{manualMatch=new ManualMatchEntry(LocalDate.now(),f);showManualMatch();});backButton(content,tr("Cancel"),pastMatchReturn);}
    private void chooseGenericScore(String title,String opponent,MatchFormat f,boolean tie,int me,int other,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title,"set-winner:"+title);paragraph(content,tr(f.name())+" · "+(tie?tr("Match tie-break to 10"):ui.rules(f))+tr("\nChoose the winner, then the losing score."));
        navigationButton(content,tr("Me / our team won"),()->genericScoreWheel(title,opponent,f,tie,0,Math.min(me,other),apply,back));navigationButton(content,tr("Opponent / other team won"),()->genericScoreWheel(title,opponent,f,tie,1,Math.min(me,other),apply,back));backButton(content,tr("Cancel"),back);
    }
    private void genericScoreWheel(String title,String opponent,MatchFormat f,boolean tie,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title,"set-points:"+title);int max=f.nested()&&!tie?6:Math.min(4999,tie?4999:f.cap()-1);NumberPicker wheel=ManualEntryUi.wheel(content,tr("Losing score"),0,max,Math.min(max,initial));TextView preview=paragraph(content,"");Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);preview.setText((winner==0?tr("Me / our team "):tr("Opponent / other team "))+high+"–"+low);};wheel.setOnValueChangedListener((picker,a,b)->refresh.run());refresh.run();
        primaryButton(content,tr("Use score"),()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);apply.accept(winner==0?high:low,winner==0?low:high);});backButton(content,tr("Cancel"),back);
    }
    private String updateDetails(){
        String text=tr("Installed: v")+BuildConfig.VERSION_NAME;
        if(latest!=null&&latest.newerThan(BuildConfig.VERSION_CODE))text+="\n\nv"+latest.versionName+"\n"+latest.notes;
        String install=preferences.getString("install_status","");
        if(!install.isEmpty())text+="\n\n"+ui.status(install);
        return text;
    }
    private String opponentName(String matchId){return singleOpponent(matchId,true);}
    private List<MatchResult> filteredResults(){List<MatchResult> out=new java.util.ArrayList<>();for(MatchResult result:ResultFilter.select(book,opponents,filterOpponent,filterFrom,filterTo))if(result.format.sport.equals(selectedSport)&&(filterMode<0||result.format.doubles==(filterMode==1)))out.add(result);return out;}
    private List<MatchResult> sportResults(int mode){List<MatchResult> out=new java.util.ArrayList<>();for(MatchResult result:book.newestFirst())if(result.format.sport.equals(selectedSport)&&(mode<0||result.format.doubles==(mode==1)))out.add(result);return out;}
    private String modeLabel(){return filterMode<0?tr("All modes"):filterMode==0?tr("Singles"):tr("Doubles");}
    private void message(String title,String text,Runnable back){LinearLayout content=panel(title);paragraph(content,text);backButton(content,tr("Back"),back);}
    private void showOpponents(){
        LinearLayout content=panel("Players","opponents",MenuSection.PLAYERS);
        Runnable again=this::showOpponents;
        if(playerList(content,false,(rows,entry)->playerButton(rows,entry,ButtonKind.PLAYER_LINK,()->showOpponent(entry.id)))){
            TextView legend=paragraph(content,tr("Icons show sports in saved results."));legend.setTextSize(11);
        }else paragraph(content,tr("No names saved. Names are optional; you can keep playing without one."));
        section(content,"Manage names");
        primaryNavigationButton(content,tr("Add player"),()->showNameEditor(null,null,again));
        personButton(content,tr("Your name: ")+ownName(),()->editOwnName(again));
        navigationButton(content,tr("Edit names on phone"),()->openPhone("opponents",false,again,"Back to Players"));
        backButton(content,playersReturnLabel,playersReturn);
    }
    private boolean playerList(LinearLayout content,boolean archived,PlayerListPanel.Row row){
        if(!opponents.hasPlayers(archived))return false;
        new PlayerListPanel(this,content,ui,preferences,location,(order,recent)->opponents.playerList(archived,order,ui.locale,book,
                sessionOngoing()||book.current().hasPoints()||book.currentStartedAt()>0,recent?LocalDate.now():null),row);
        panel.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return true;
    }
    private void showOpponent(String id){
        OpponentDirectory.Entry entry=opponents.get(id);if(entry==null){showOpponents();return;}LinearLayout content=panel(entry.name,"player:"+id,PanelTitle.PLAYER_NAME,MenuSection.PLAYERS);paragraph(content,sportsText(entry.id));
        navigationButton(content,tr("Rename"),()->showNameEditor(id,null,()->showOpponent(id)));
        navigationButton(content,tr("Remove from player list"),()->{LinearLayout confirm=panel("Remove opponent?");paragraph(confirm,tr("Remove ")+entry.name+tr(" from future selection? Past match results keep this name and remain filterable."));deleteButton(confirm,tr("Remove name"),()->{String before=opponents.encode();opponents.archive(id);if(persistScore())showOpponents();else{opponents=OpponentDirectory.decode(before);message(tr("Save failed"),tr("The name was not removed."),()->showOpponent(id));}});backButton(confirm,tr("Cancel"),()->showOpponent(id));});
        backButton(content,"Back to Players",this::showOpponents);
    }
    private void showNameEditor(String id,String matchId,Runnable back){
        showNameEditor(id,matchId,back,null);
    }
    private String ownName(){return scoreName(null,0,true);}
    private String scoreName(String id,int slot,boolean translate){
        String value=slot==0?preferences.getString("my_name",""):id==null?"":opponents.roleName(id,slot==1?0:slot==2?1:2);
        String fallback=slot==0?"Me":slot==1?"Opponent 1":slot==2?"Partner":"Opponent 2";
        return value.isEmpty()?(translate?tr(fallback):fallback):value;
    }
    private String singleOpponent(String id,boolean translate){
        String name=opponents.nameFor(id);
        return name.isEmpty()?(translate?tr("Opponent"):"Opponent"):name;
    }
    private void editOwnName(Runnable back){
        LinearLayout content=panel("Your name",null,MenuSection.PLAYERS);paragraph(content,tr("Shown on your score card and shared scoreboards. Leave blank to use Me."));
        nameInput=menuInput(content,tr("Your name"),tr("Your name"),android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setText(preferences.getString("my_name",""));
        final EditText input=nameInput;
        primaryButton(content,tr("Save your name"),()->{try{String value=input.getText().toString().trim();if(!value.isEmpty())value=OpponentDirectory.validName(value);if(!preferences.edit().putString("my_name",value).commit())throw new IllegalStateException(tr("Name was not saved"));updateDisplay();back.run();}catch(Exception e){input.setError(safe(e));}});
        backButton(content,tr("Cancel"),back);
    }
    private void showNameEditor(String id,String matchId,Runnable back,java.util.function.Consumer<String> afterSave){
        OpponentDirectory.Entry entry=id==null?null:opponents.get(id);LinearLayout content=panel(entry==null?tr("Add player"):tr("Rename player"),null,MenuSection.PLAYERS);
        nameInput=menuInput(content,tr("Player name"),tr("Player name"),android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if(entry!=null)nameInput.setText(entry.name);
        final EditText input=nameInput;
        secondaryButton(content,tr("Speak name"),()->{try{Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,tr("Player name"));startActivityForResult(voice,81);}catch(Exception absent){input.setError(tr("Speech input is unavailable. Type a name or use Edit names on phone."));}});
        primaryButton(content,tr("Save name"),()->{String before=opponents.encode();try{String selected=id;if(selected==null)selected=opponents.add(input.getText().toString());else opponents.rename(selected,input.getText().toString());if(matchId!=null)opponents.assign(matchId,selected);if(!persistScore())throw new IllegalStateException(tr("The watch did not confirm saving"));if(afterSave!=null)afterSave.accept(selected);updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);input.setError(safe(error));}});
        paragraph(content,tr("You can also type names from Opponents → Edit names on phone."));backButton(content,tr("Cancel"),back);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK&&data!=null&&nameInput!=null){java.util.ArrayList<String> names=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(names!=null&&!names.isEmpty())nameInput.setText(names.get(0));}}
    private void showOpponentPicker(String matchId,boolean filtering){
        showOpponentPicker(matchId,filtering,filtering?this::showResultFilters:matchId==null?this::showNewMatchOptions:this::showMatchOptions);
    }
    private void showOpponentPicker(String matchId,boolean filtering,Runnable back){
        boolean starting=!filtering&&matchId==null;
        LinearLayout content=panel(filtering?tr("Filter by opponent"):tr("Choose opponent"),filtering?"filter-opponent":starting?"new-opponent":"match-opponent:"+matchId,MenuSection.PLAYERS);
        String selected=filtering?filterOpponent:starting?newMatchOpponent:opponents.opponentId(matchId);
        if(filtering)playerChoice(content,tr("All opponents"),selected==null,()->{filterOpponent=null;resultsPage=0;showResultFilters();});
        playerChoice(content,filtering?tr("Unnamed"):tr("No name"),"".equals(selected),()->{if(filtering){filterOpponent="";resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent="";showNewMatchOptions();}else assignOpponent(matchId,"",back);});
        boolean hasNames=playerList(content,filtering,(rows,e)->savedPlayerChoice(rows,e.name+(e.archived?tr(" (removed)"):""),e.id.equals(selected),()->{
            if(filtering){filterOpponent=e.id;resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent=e.id;showNewMatchOptions();}else assignOpponent(matchId,e.id,back);}));
        if(!filtering&&hasNames)section(content,"Manage names");
        if(starting)navigationButton(content,tr("Add player"),()->showNameEditor(null,null,this::showNewMatchOptions,id->newMatchOpponent=id));
        else if(!filtering){navigationButton(content,tr("Add player"),()->showNameEditor(null,matchId,back));navigationButton(content,tr("Edit names on phone"),()->openPhone("opponents",false,()->showOpponentPicker(matchId,false,back),"Back to Choose opponent"));}
        backButton(content,tr("Cancel"),back);
    }
    private void assignOpponent(String matchId,String id,Runnable back){String before=opponents.encode();try{opponents.assign(matchId,id);if(!persistScore())throw new IllegalStateException(tr("The watch did not confirm saving"));updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);message(tr("Name not saved"),safe(error),back);}}
    private void showResultFilters(){
        LinearLayout content=panel("Result filters",null,MenuSection.RESULTS);
        secondaryButton(content,tr("Reset all filters"),this::resetResultFilters);
        section(content,"Match");
        if(!selectedSport.equals("squash")&&!selectedSport.equals("padel"))navigationButton(content,tr("Mode: ")+modeLabel(),()->chooseResultMode(this::showResultFilters));
        OpponentDirectory.Entry e=filterOpponent==null?null:opponents.get(filterOpponent);
        personButton(content,tr("Opponent: ")+(filterOpponent==null?tr("All"):filterOpponent.isEmpty()?tr("Unnamed"):e==null?tr("Unknown"):e.name),()->showOpponentPicker(null,true));
        section(content,"Date range");
        paragraph(content,tr("Dates: ")+(filterFrom==null?tr("any"):ui.filterDate(filterFrom))+tr(" to ")+(filterTo==null?tr("any"):ui.filterDate(filterTo)));
        secondaryButton(content,tr("Today"),()->{filterFrom=LocalDate.now();filterTo=filterFrom;showResults(0);});
        secondaryButton(content,tr("Last 7 days"),()->{filterTo=LocalDate.now();filterFrom=filterTo.minusDays(6);showResults(0);});
        secondaryButton(content,tr("This month"),()->{filterTo=LocalDate.now();filterFrom=filterTo.withDayOfMonth(1);showResults(0);});
        navigationButton(content,tr("Custom date range"),this::showDateRange);
        backButton(content,"Back to Results",()->showResults(resultsPage));
    }
    private boolean resultFiltersActive(){return filterOpponent!=null||filterFrom!=null||filterTo!=null||filterMode>=0;}
    private void resetResultFilters(){
        filterOpponent=null;filterFrom=filterTo=null;filterMode=-1;showResultsFromTop(0);
    }
    private void showDateRange(){
        LinearLayout content=panel("Date range");paragraph(content,tr("Use YYYY-MM-DD. Leave either date empty for no limit."));
        int type=android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE;
        EditText from=menuInput(content,tr("From: YYYY-MM-DD"),tr("From date"),type);
        EditText to=menuInput(content,tr("To: YYYY-MM-DD"),tr("To date"),type);
        if(filterFrom!=null)from.setText(filterFrom.toString());if(filterTo!=null)to.setText(filterTo.toString());
        primaryButton(content,tr("Apply dates"),()->{try{LocalDate a=from.getText().toString().trim().isEmpty()?null:LocalDate.parse(from.getText().toString().trim()),b=to.getText().toString().trim().isEmpty()?null:LocalDate.parse(to.getText().toString().trim());if(a!=null&&b!=null&&a.isAfter(b))throw new IllegalArgumentException(tr("From date must not follow To date"));filterFrom=a;filterTo=b;showResults(0);}catch(Exception invalid){from.setError(tr("Check both dates: YYYY-MM-DD, From before To"));}});backButton(content,tr("Cancel"),this::showResultFilters);
    }
    private org.json.JSONObject liveSnapshot()throws Exception{
        boolean remote=rooms!=null && rooms.isHost() && rooms.displayed()!=null;
        MatchState m=remote?rooms.displayed():book.current();String phase=m.complete()?"finished":m.nextReady()?"interval":remote?"playing":needsResume()?"paused":sessionOngoing()?"playing":"ready";org.json.JSONArray scores=new org.json.JSONArray();for(int i=0;i<m.gameCount();i++)scores.put(new org.json.JSONObject().put("a",m.game(i).blue()).put("b",m.game(i).red()).put("matchTie",m.matchTie(i)).put("complete",m.game(i).winner()>=0));
        int server=m.complete()||m.game().winner()>=0?-1:m.server();org.json.JSONObject snapshot=new org.json.JSONObject().put("schema",1).put("matchId",m.id()).put("sport",m.format().sport).put("mode",m.format().doubles?"doubles":"singles").put("rules",m.format().description()).put("format",m.format().encode()).put("names",new org.json.JSONArray().put(m.format().doubles?teamName(m.id(),0,false):scoreName(null,0,false)).put(m.format().doubles?teamName(m.id(),1,false):singleOpponent(m.id(),false))).put("points",new org.json.JSONArray().put(m.game().displayPoint(0)).put(m.game().displayPoint(1))).put("sets",new org.json.JSONArray().put(m.gamesWon(0)).put(m.gamesWon(1))).put("setScores",scores).put("bestOf",m.bestOf()).put("server",server).put("serveName",slotName(server,m.id(),false)).put("scoreCall",m.format().pickleball()?m.game().pickleCall():"").put("phase",phase).put("winner",m.winner()).put("startedAt",remote?rooms.time("startedAt"):book.currentStartedAt()).put("finishedAt",remote?rooms.time("finishedAt"):book.currentFinishedAt()).put("retiredPlayer",m.retiredPlayer());
        snapshot.put("labels",new org.json.JSONArray().put(preferences.getString("my_name","")).put(opponents.roleName(m.id(),0)).put(opponents.roleName(m.id(),1)).put(opponents.roleName(m.id(),2)));
        if(remote)snapshot.put("names",new org.json.JSONArray().put(rooms.team(0)).put(rooms.team(1))).put("serveName",rooms.serveName(server));
        HeadToHead history=HeadToHead.from(m,book.newestFirst(),opponents);
        if(history!=null){MatchResult last=history.last;org.json.JSONArray previous=new org.json.JSONArray();for(int i=0;i<last.gameCount();i++)previous.put(new org.json.JSONObject().put("a",last.points(i,0)).put("b",last.points(i,1)).put("matchTie",last.isMatchTie(i)).put("complete",last.setComplete(i)));
            snapshot.put("headToHead",new org.json.JSONObject().put("won",history.won).put("lost",history.lost).put("last",new org.json.JSONObject().put("date",last.date).put("sets",new org.json.JSONArray().put(last.gamesWon(0)).put(last.gamesWon(1))).put("scores",previous).put("winner",last.winner()).put("retiredPlayer",last.retiredPlayer)));}
        return snapshot;
    }

    org.json.JSONObject roomSnapshot() throws Exception {
        MatchState m=book.current();
        org.json.JSONArray labels=new org.json.JSONArray().put(preferences.getString("my_name",""))
            .put(opponents.roleName(m.id(),0)).put(opponents.roleName(m.id(),1)).put(opponents.roleName(m.id(),2));
        String zone=book.currentZoneId().isEmpty()?java.time.ZoneId.systemDefault().getId():book.currentZoneId();
        return new org.json.JSONObject().put("schema",1).put("match",m.encode()).put("labels",labels)
            .put("startedAt",book.currentStartedAt()).put("finishedAt",book.currentFinishedAt())
            .put("zone",zone).put("final",m.complete());
    }
    String roomMatchId(){return book.current().id();}
    boolean hasAudienceLink(){return live.session()!=null&&live.session().matchId.equals(roomMatchId());}
    private void sharingButton(LinearLayout page,MatchRooms.Entry entry){
        navigationButton(page,rooms.title(),()->{scrollPositions.remove("room-hub");rooms.open(entry);});
    }
    void sharingBack(LinearLayout page,MatchRooms.Entry entry){
        switch(entry){
            case SPORTS:backButton(page,"Back to sports",this::showSports);break;
            case SPORT:backButton(page,"Back to Menu",this::showMenu);break;
            case MATCH:backButton(page,"Back to Current match",this::showMatchControls);break;
            case SHARED:backButton(page,"Back to Menu",rooms::menu);break;
        }
    }
    void chooseSharingMatch(MatchRooms.Entry entry,boolean audience,String createLabel,Runnable ready){
        if(rooms.readOnly()){rooms.hub();return;}
        MatchState current=book.current();
        boolean usable=!current.complete()&&(hasCurrentMatch()||entry==MatchRooms.Entry.MATCH);
        boolean audienceFinal=audience&&current.complete()&&(entry==MatchRooms.Entry.MATCH||hasAudienceLink());
        if(usable||audienceFinal){ready.run();return;}
        if(entry!=MatchRooms.Entry.SPORTS){
            showSharingSetup(rooms::hub,ready,createLabel,location);
            return;
        }
        LinearLayout page=roomPanel(tr("Choose sport"),"sharing-sport");
        for(String sport:ui.sortedSports()){
            Button choice=navigationButton(page,tr(MatchFormat.name(sport)),()->{
                if(!selectSport(sport))return;
                location.title=MatchFormat.name(sport);
                showSharingSetup(()->chooseSharingMatch(entry,audience,createLabel,ready),ready,createLabel,location.parent);
            });
            sportButton(choice,sport);
        }
        backButton(page,tr("Back"),rooms::hub);
    }
    private void showSharingSetup(Runnable back,Runnable ready,String createLabel,MenuLocation hub){
        sharedArea=false;
        showNewMatch(back,()->{
            // The setup is complete. Start the sharing screen from the hub.
            location=hub;
            ready.run();
        },createLabel);
    }
    boolean roomAlreadySaved(String id){return book.result(id)!=null;}
    boolean roomLocalFinished(){return book.current().complete();}
    boolean roomPageVisible(View page){return panel!=null && panel.isShowing() && page.getRootView()==panel.getWindow().getDecorView();}
    boolean roomPanelVisible(String key){return panel!=null && key.equals(panelScrollKey);}
    boolean roomMenuVisible(){return roomPanelVisible("room-menu");}
    boolean roomCancelVisible(){return roomPanelVisible("room-cancel");}
    boolean roomScoreVisible(){return panel==null;}
    void refreshRoomScore(){updateDisplay();}
    String roomSport(String sport){return tr(MatchFormat.name(sport));}
    void restoreLocalScore(){counter.match(book.current());counter.remote(null,false);updateDisplay();}
    LinearLayout roomPanel(String title,String key){
        if("room-menu".equals(key) && (panel==null || roomCancelVisible()))scrollPositions.remove(key);
        sharedArea=true;
        MenuSection section=("room-menu".equals(key)||"room-details".equals(key))?MenuSection.CURRENT_MATCH:
                "room-seat".equals(key)?MenuSection.RESULTS:"sharing-sport".equals(key)?MenuSection.SPORTS:MenuSection.SHARING;
        LinearLayout content=panel(title,key,section);panelShortcut=false;return content;
    }
    void roomMessage(String msg,Runnable back){
        LinearLayout p=roomPanel(rooms.title(),"room-message");paragraph(p,msg);
        backButton(p,rooms.text("Back","Zurück","Retour","Volver"),back);
    }
    void pauseForRoom(){
        if(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH))setSession(false,true);
    }
    void saveRoomResult(MatchState match,org.json.JSONObject data,int seat,boolean host)throws Exception{
        if(book.result(match.id())!=null)return;
        MatchBook next=MatchBook.decode(book.encode());OpponentDirectory names=OpponentDirectory.decode(opponents.encode());
        String[] labels=new String[4];for(int i=0;i<4;i++)labels[i]=data.getJSONArray("labels").getString(i);
        SharedResult.save(next,names,match,labels,data.getLong("startedAt"),data.getLong("finishedAt"),data.getString("zone"),seat,host);
        commitRoomBooks(next,names,host);
    }
    void discardRoomDraft(String id)throws Exception{
        if(!book.current().id().equals(id) || book.current().complete())return;
        MatchBook next=MatchBook.decode(book.encode());OpponentDirectory names=OpponentDirectory.decode(opponents.encode());
        next.discardCurrent(UUID.randomUUID().toString());names.forgetMatch(id);commitRoomBooks(next,names,true);
        if(live!=null)live.discard();
    }
    private void commitRoomBooks(MatchBook next,OpponentDirectory names,boolean resetCurrent)throws Exception{
        SharedPreferences.Editor edit=preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode());
        if(resetCurrent)edit.remove(MatchDisplayPolicy.ACTIVE_MATCH).remove("match_ready_id").putBoolean("match_paused",false);
        if(!edit.commit())throw new java.io.IOException("Could not save match history");
        book=next;opponents=names;counter.match(book.current());
    }
    void openSavedRoomResult(String id,String sport){
        restoreLocalScore();selectedSport=sport;sharedArea=false;
        if(!sportLocked()&&!unsavedFinish()){FinishedMatchReview.openShared(preferences,book,id,System.currentTimeMillis());checkFinishedReview();}
        showResult(id,0,this::showSports,rooms.text("Back to sports","Zur Sportauswahl","Retour aux sports","Volver a deportes"));
    }

    private String liveState(LiveScoreClient.Session s){return s==null?"none":s.id+(s.ready?":ready":":creating");}
    private void onLiveStatusChanged(String ignored){
        if(!resumed||ambient||isFinishing()||isDestroyed()||panel==null||!panel.isShowing()||!panelPage.equals("Live scoreboard"))return;
        LiveScoreClient.Session s=live.session();String state=liveState(s);
        if(!state.equals(livePanelState)){
            if(s!=null&&s.ready&&livePanelState.equals(s.id+":creating"))showAudienceQr(s);else showLive();
        }else if(liveText!=null)liveText.setText(tr(live.status()));
    }
    private void showAudienceQr(LiveScoreClient.Session s){if(s!=live.session()||!s.ready||!s.matchId.equals(book.current().id()))return;showQr(s.link(),tr("Scan to follow live"),this::showLive,"Back to Live scoreboard");}
    void showLive(){
        if(rooms.readOnly()&&!rooms.isHost()){rooms.hub();return;}
        LinearLayout c=panel("Live scoreboard","live",MenuSection.SHARING);liveText=paragraph(c,tr(live.status()));LiveScoreClient.Session s=live.session();livePanelState=liveState(s);
        if(s!=null){if(s.ready){primaryNavigationButton(c,tr("Show audience QR"),()->showAudienceQr(s));
            if(nfc.canSend())navigationButton(c,tr("Share page via NFC"),()->nfc.shareLink(s.link(),this::showLive,"Back to Live scoreboard"));
            paragraph(c,tr("Anyone with this link can view this match’s score, names and previous-meeting summary. Live links last up to eight hours; final results remain for ten minutes after completion. Audience members cannot change the score."));}else paragraph(c,tr("Creating your audience link… The QR will open when it is ready."));secondaryButton(c,tr("Stop sharing"),()->{live.stop(true);showLive();});}
        else{primaryButton(c,tr("Start live sharing"),()->{try{live.start(liveSnapshot());LiveScoreClient.Session started=live.session();if(started!=null&&started.ready)showAudienceQr(started);else showLive();}catch(Exception e){message(tr("Cannot share"),safe(e),this::showLive);}});paragraph(c,tr("Share this match’s score, names and head-to-head summary with an audience. Previous meetings use the same sport and saved players; doubles requires both teams to match. Start with an internet connection, then share the QR link. Offline scores and final results retry when the connection returns."));}
        if(s!=null && s.ready && !book.current().complete())rooms.watchInvitesFromLive(c);
        backButton(c,rooms.backToSharing(),rooms::hub);
    }
    private java.util.Map<String,String> backupSettings(){java.util.Map<String,String> out=new java.util.TreeMap<>();for(java.util.Map.Entry<String,?> e:preferences.getAll().entrySet())if(BackupArchive.settingKey(e.getKey()))out.put(e.getKey(),String.valueOf(e.getValue()));return out;}
    private String backupOverride="",transferBackupOverride="";
    private void showBackup(){LinearLayout c=panel("Backup & restore");paragraph(c,tr("A complete backup includes all sports, players, your name, favourite formats, settings and the current match with Undo. Keep downloaded backups private."));primaryNavigationButton(c,tr("Create or restore backup"),()->{backupOverride="";openPhone("backup",false,this::showBackup,"Back to Backup");});paragraph(c,tr("Scan the phone QR to download or choose a backup. Restoring replaces local data after review; unfinished matches return paused."));if(preferences.contains("backup_before_restore"))navigationButton(c,tr("Download previous data copy"),()->{backupOverride=preferences.getString("backup_before_restore","");openPhone("backup",false,this::showBackup,"Back to Backup");});backButton(c,"Back to Settings",this::showSettings);}
    private void previewBackupRestore(org.json.JSONObject payload,TransferClient.Session session)throws Exception{
        if(!transferSnapshotBackup)throw new IllegalArgumentException(tr("Start Backup & restore on the watch first"));
        if(!payload.getString("baseDirectory").equals(TransferClient.base(book,opponents))||!payload.getString("currentMatchId").equals(book.current().id()))throw new IllegalArgumentException(tr("Watch data changed. Start a fresh backup link."));
        final BackupArchive restored=BackupArchive.parse(payload.getString("restoreBackup"));final String before=book.encode(),namesBefore=opponents.encode();final java.util.Map<String,String> settingsSnapshot=backupSettings();final String settingsBefore=BackupArchive.create(book,opponents,settingsSnapshot);
        LinearLayout c=panel("Restore this backup?");paragraph(c,tr("Backup from ")+restored.createdAt+"\n"+restored.book.count()+tr(" saved matches\n")+restored.directory.list(true).size()+tr(" players\nYour name: ")+restored.settings.getOrDefault("my_name",tr("Me")));paragraph(c,tr("Replace all local match history, players, settings and the current match? Current local data will be kept as a recovery copy. An unfinished restored match will be paused."));
        primaryButton(c,tr("Replace data & restore"),()->{
            try{if(session.expired()||!before.equals(book.encode())||!namesBefore.equals(opponents.encode())||!settingsSnapshot.equals(backupSettings()))throw new IllegalArgumentException(tr("Watch data changed or the link expired. Receive a fresh preview."));
                SharedPreferences.Editor edit=preferences.edit();for(String k:preferences.getAll().keySet())if(BackupArchive.settingKey(k))edit.remove(k);
                for(java.util.Map.Entry<String,String> e:restored.settings.entrySet()){String k=e.getKey(),v=e.getValue();if(k.equals("last_best_of")||k.startsWith("last_best_"))edit.putInt(k,Integer.parseInt(v));else if(k.equals("match_keep_awake_v3")||k.equals("match_reminders")||k.equals(ScoreSpeech.PREFERENCE)||k.equals("match_paused"))edit.putBoolean(k,Boolean.parseBoolean(v));else edit.putString(k,v);}
                edit.putString("match_book",restored.book.encode()).putString("opponent_book",restored.directory.encode()).putString("backup_before_restore",settingsBefore).remove(MatchDisplayPolicy.ACTIVE_MATCH).putBoolean("match_paused",!restored.book.current().complete()&&(restored.book.current().hasPoints()||restored.settings.containsKey("match_ready_id")));
                if(!edit.commit())throw new IllegalStateException(tr("Restore was not saved. Your previous data is kept."));if(live!=null)live.stop(true);configureSpeech();book=restored.book;opponents=restored.directory;selectedSport=sportLocked()?book.current().format().sport:restored.settings.getOrDefault("selected_sport",book.current().format().sport);manualMatch=null;transfer=null;counter.match(book.current());updateDisplay();tasks.execute(()->{try{TransferClient.end(session);}catch(Exception ignored){}});message(tr("Backup restored"),book.count()+tr(" results and ")+opponents.list(true).size()+tr(" players restored. Resume your match when ready."),this::showBackup);
            }catch(Exception e){message(tr("Backup not restored"),safe(e),this::showBackup);}
        });backButton(c,tr("Cancel"),this::showTransfer);
    }
    private void openPhone(String view,boolean filtered,Runnable back,String backLabel){openPhone(view,filtered,back,backLabel,"");}
    private void openPhone(String view,boolean filtered,Runnable back,String backLabel,String resultId){
        transferResultId=resultId;
        if(transferBusy){message(tr("Transfer in progress"),tr("Wait for the current request, then open the phone link again."),back);return;}
        transferView=view;transferFiltered=filtered;transferReturn=back;transferReturnLabel=backLabel;beginTransfer(filtered);
    }
    private void beginTransfer(boolean filtered){
        if(transferBusy)return;
        if(transfer!=null&&!transfer.expired()){
            if(transferSnapshotBackup!=transferView.equals("backup")||transferView.equals("backup")&&!transferBackupOverride.equals(backupOverride)||filtered||transferSnapshotFiltered&&!transferView.equals("opponents"))message(tr("Transfer already open"),tr("End the current transfer first, then export your selected results. This keeps its existing phone changes available until you finish."),this::showTransfer);
            else showTransfer();return;
        }
        final String saved=book.encode(),names=opponents.encode(),myName=preferences.getString("my_name","");final boolean backupMode=transferView.equals("backup");final String backupCopy=backupOverride;final java.util.Map<String,String> backupSettings=backupSettings();final List<MatchResult> selected=backupMode?java.util.Collections.emptyList():filtered?new java.util.ArrayList<>(filteredResults()):ResultFilter.select(book,opponents,null,null,null);
        transferBusy=true;LinearLayout content=panel("Preparing phone link","phone-transfer:"+transferView+(transferResultId.isEmpty()?"":":card"),phoneSection());paragraph(content,tr("Creating an encrypted ten-minute link. Both devices need internet."));backButton(content,transferReturnLabel,transferReturn);
        final Dialog waiting=panel;
        tasks.execute(()->{try{TransferClient.Session created=TransferClient.create(MatchBook.decode(saved),OpponentDirectory.decode(names),selected,selectedSport,myName,backupMode?(backupCopy.isEmpty()?BackupArchive.create(MatchBook.decode(saved),OpponentDirectory.decode(names),backupSettings):backupCopy):null);onUi(()->transferCreated(created,filtered,backupMode,backupCopy,waiting));}catch(Exception error){onUi(()->{transferBusy=false;if(panel==waiting)message(tr("Transfer unavailable"),safe(error),transferReturn);});}});
    }
    private void transferCreated(TransferClient.Session created,boolean filtered,boolean backupMode,String backupCopy,Dialog waiting){transferBusy=false;transfer=created;transferBackupOverride=backupCopy;transferSnapshotFiltered=filtered;transferSnapshotBackup=backupMode;if(panel==waiting)showTransfer();}
    private MenuSection phoneSection(){
        if(!transferResultId.isEmpty())return MenuSection.SHARING;
        if(transferView.equals("opponents"))return MenuSection.PLAYERS;
        if(transferView.equals("backup"))return MenuSection.SETTINGS;
        return MenuSection.RESULTS;
    }
    private String phoneTitle(){return transferView.equals("backup")?tr("Backup & restore"):transferView.equals("opponents")?tr("Edit names on phone"):transferView.equals("import")?tr("Import CSV"):tr("Results on phone");}
    private String phoneLink(){return transfer.link()+"&view="+transferView+(transferResultId.isEmpty()?"":"&match="+transferResultId);}
    private void showTransfer(){
        LinearLayout content=panel(phoneTitle(),"phone-transfer:"+transferView+(transferResultId.isEmpty()?"":":card"),phoneSection());
        if(transfer==null||transfer.expired()){
            paragraph(content,tr("No active phone link. Create an encrypted link that expires after ten minutes."));
            primaryButton(content,tr("Create phone link"),()->{transfer=null;beginTransfer(transferFiltered);});
        }else{
            primaryNavigationButton(content,tr("Show phone QR"),()->showQr(phoneLink(),tr("Scan with phone"),this::showTransfer,"Back to phone link"));
            if(nfc.canSend())navigationButton(content,tr("Share page via NFC"),
                ()->{if(transfer!=null && !transfer.expired())nfc.shareLink(phoneLink(),this::showTransfer,"Back to phone link");else showTransfer();});
            String purpose=transferView.equals("backup")?tr("Download a complete backup, or select a backup file to restore. Restore requires a separate watch confirmation."):transferView.equals("opponents")?tr("Edit saved names in your phone browser."):transferView.equals("import")?tr("Select a Keep Score CSV in your phone browser."):tr("Add or edit results, browse statistics or download CSV in your browser.");
            paragraph(content,purpose+tr("\n\nExpires ")+java.time.Instant.ofEpochMilli(transfer.expiresAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+tr(". Send edits on the phone, then receive them here."));
            section(content,"On this watch");
            Button receive=navigationButton(content,transferBusy?tr("Receiving…"):tr("Receive changes"),this::receiveTransfer);receive.setEnabled(!transferBusy);
            secondaryButton(content,tr("End transfer"),()->{if(transferBusy)return;final TransferClient.Session ending=transfer;transfer=null;tasks.execute(()->{try{TransferClient.end(ending);}catch(Exception ignored){}});showTransfer();});
        }
        backButton(content,transferReturnLabel,transferReturn);
    }
    private void receiveTransfer(){
        if(transferBusy||transfer==null)return;final TransferClient.Session session=transfer;transferBusy=true;showTransfer();final Dialog waiting=panel;
        tasks.execute(()->{try{org.json.JSONObject payload=TransferClient.receive(session);onUi(()->{transferBusy=false;if(panel!=waiting)return;try{previewTransfer(payload,session);}catch(Exception error){message(tr("Cannot apply changes"),safe(error),this::showTransfer);}});}catch(Exception error){onUi(()->{transferBusy=false;if(panel==waiting)message(tr("Nothing received"),safe(error),this::showTransfer);});}});
    }
    private void previewTransfer(org.json.JSONObject payload,TransferClient.Session session)throws Exception{
        if(payload.has("restoreBackup")){previewBackupRestore(payload,session);return;}
        final String oldMyName=preferences.getString("my_name","");final boolean changeMyName=payload.optBoolean("changeMyName",false);final String nextMyName=changeMyName?payload.getString("myName").trim():oldMyName;
        if(changeMyName){if(!oldMyName.equals(payload.getString("baseMyName")))throw new IllegalArgumentException(tr("Your name changed. Start a fresh phone link."));if(!nextMyName.isEmpty())OpponentDirectory.validName(nextMyName);}
        final String before=book.encode(),names=opponents.encode();final MatchCsv.Plan plan=TransferClient.preview(payload,book,opponents,session.sharedResults);
        LinearLayout content=panel("Review phone changes");if(changeMyName)paragraph(content,tr("Your name: ")+(oldMyName.isEmpty()?tr("Me"):oldMyName)+" → "+(nextMyName.isEmpty()?tr("Me"):nextMyName));paragraph(content,plan.added+tr(" new results\n")+plan.duplicates+tr(" duplicates skipped\n")+plan.conflicts+tr(" conflicts skipped\n")+plan.renumbered+tr(" local references adjusted\n\n")+plan.directory.list(false).size()+(plan.editedIds.isEmpty()?tr(" saved opponent names\n\nNames may be added, renamed or removed from selection. Past results and the ongoing score are kept."):tr(" saved opponent names")));
        if (!plan.editedIds.isEmpty()) {
            paragraph(content, tr("Edited results") + " · " + plan.editedIds.size());
            for (String id : plan.editedIds.subList(0, Math.min(10, plan.editedIds.size()))) {
                MatchResult old = book.result(id), updated = plan.book.result(id);
                paragraph(content, old.referenceText() + "\n" + resultReview(old, opponents) + "\n→ " + resultReview(updated, plan.directory));
            }
        }
        boolean personChanged=!opponents.opponentId(book.current().id()).equals(plan.directory.opponentId(book.current().id()));
        if(personChanged)paragraph(content,(book.current().complete()?tr("Last completed match"):tr("Current match"))+tr(" opponent change:\n")+opponentName(book.current().id())+" → "+(plan.directory.nameFor(book.current().id()).isEmpty()?tr("No name"):plan.directory.nameFor(book.current().id())));
        else paragraph(content,tr("Match opponent assignment is unchanged."));
        java.util.ArrayList<String> changedNames=new java.util.ArrayList<>();for(OpponentDirectory.Entry e:plan.directory.list(true)){OpponentDirectory.Entry old=opponents.get(e.id);if(old==null)changedNames.add(tr("Add ")+e.name+(e.archived?tr(" (removed)"):""));else if(!old.name.equals(e.name)||old.archived!=e.archived)changedNames.add((old.name.equals(e.name)?e.name:old.name+" → "+e.name)+(e.archived?tr(" (removed)"):old.archived?tr(" (restored)"):""));}
        if(!changedNames.isEmpty())paragraph(content,tr("Name changes:\n")+android.text.TextUtils.join("\n",changedNames.subList(0,Math.min(10,changedNames.size())))+(changedNames.size()>10?tr("\n… and ")+(changedNames.size()-10)+tr(" more"):""));
        if(!plan.conflictIds.isEmpty())paragraph(content,tr("Conflicting IDs:\n")+android.text.TextUtils.join("\n",plan.conflictIds.subList(0,Math.min(3,plan.conflictIds.size()))));
        primaryButton(content,tr("Apply changes"),()->{
            if(!before.equals(book.encode())||!names.equals(opponents.encode())||!oldMyName.equals(preferences.getString("my_name",""))){message(tr("Data changed"),tr("Receive again to build a fresh preview."),this::showTransfer);return;}
            if(session.expired()){message(tr("Transfer expired"),tr("Start a new transfer. Nothing was changed."),this::showTransfer);return;}
            if(!preferences.edit().putString("match_book",plan.book.encode()).putString("opponent_book",plan.directory.encode()).putString("my_name",nextMyName).commit()){message(tr("Save failed"),tr("Nothing was confirmed. Keep the app open and retry."),this::showTransfer);return;}
            book=plan.book;opponents=plan.directory;counter.match(book.current());updateDisplay();transfer=null;
            tasks.execute(()->{try{TransferClient.end(session);}catch(Exception ignored){}});
            message(tr("Changes saved"),plan.added+tr(" results added. Opponents updated. Duplicate and conflicting results were skipped.")+(plan.editedIds.isEmpty()?"":"\n"+tr("Edited results")+" · "+plan.editedIds.size()),transferReturn);
        });backButton(content,tr("Cancel"),this::showTransfer);
    }
    private String resultReview(MatchResult result, OpponentDirectory names) {
        java.util.ArrayList<String> scores = new java.util.ArrayList<>();
        for (int i = 0; i < result.gameCount(); i++) scores.add(result.points(i, 0) + "–" + result.points(i, 1));
        String partner = names.roleName(result.id, 1), rival = names.roleName(result.id, 0), other = names.roleName(result.id, 2);
        String teams = tr("Me") + (partner.isEmpty() ? "" : " + " + partner) + " / " +
                (rival.isEmpty() ? tr("Opponent") : rival) + (other.isEmpty() ? "" : " + " + other);
        return result.date + " · " + teams + "\n" + android.text.TextUtils.join(", ", scores) +
                (result.retired() ? " · " + tr(result.retiredPlayer == 0 ? "Me retired" : "Opponent retired") : "");
    }
    private void showUpdates(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        LinearLayout content = panel("App updates", null, downloading ? PanelTitle.CUSTOM : PanelTitle.STANDARD);
        updatePanel = true;
        if (downloading) {
            panelScroll.setVerticalScrollBarEnabled(false);
            TextView title=panelHeading(content,tr("App updates"),true);
            bindMenuPath(content,title,tr("App updates"));
        }
        updateBar = (ProgressBar) getLayoutInflater().inflate(R.layout.update_progress, content, false);
        content.addView(updateBar);
        updateSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        updateSpinner.setIndeterminateTintList(updateBar.getIndeterminateTintList());
        updateSpinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(updateSpinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        updateText = paragraph(content, "");
        renderUpdateStatus();
        if (downloading) {
            backButton(content, "Back to Settings", this::showSettings);
            return;
        }
        paragraph(content, updateDetails());
        String checkLabel=tr(checking?"Checking…":"Check for updates");
        boolean allowed=getPackageManager().canRequestPackageInstalls();
        boolean offered=latest!=null&&latest.newerThan(BuildConfig.VERSION_CODE)&&latest.supports(Build.VERSION.SDK_INT);
        if(offered){
            secondaryButton(content,checkLabel,this::checkUpdates);
            if(allowed)primaryButton(content,tr("Download & update"),this::downloadUpdate);
            else primaryNavigationButton(content,tr("Allow app updates"),this::allowUpdates);
        }else{
            primaryButton(content,checkLabel,this::checkUpdates);
            if(!allowed)navigationButton(content,tr("Set up direct updates"),this::allowUpdates);
        }
        navigationButton(content,tr("Phone install help"),this::phoneHelp);backButton(content,"Back to Settings",this::showSettings);
    }
    private void checkUpdates(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        if(checking||downloading)return;checking=true;setUpdateStatus("Checking release website…");
        preferences.edit().putLong("last_check",System.currentTimeMillis()).apply();
        tasks.execute(()->{
            try{
                UpdatePolicy release=updater.check();
                onUi(()->{
                    checking=false;latest=release;
                    counter.available(release.newerThan(BuildConfig.VERSION_CODE)&&release.supports(Build.VERSION.SDK_INT));
                    if (downloading) return;
                    setUpdateStatus(!release.supports(Build.VERSION.SDK_INT)?"New release needs a newer watch OS."
                        :release.newerThan(BuildConfig.VERSION_CODE)?"A new version is ready.":"You have the latest version.");
                    if(updatePanel)showUpdates();
                });
            }catch(Exception error){onUi(()->{checking=false;if(downloading)return;setUpdateStatus("Check failed: "+error.getMessage());if(updatePanel)showUpdates();});}
        });
    }
    private void downloadUpdate(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        if(downloading||latest==null)return;
        if(!getPackageManager().canRequestPackageInstalls()){allowUpdates();return;}
        downloading=true;persistScore();setUpdateStatus("Downloading verified update…", 0);
        if (updatePanel) showUpdates();
        final UpdatePolicy release=latest;
        tasks.execute(()->{
            try{
                File apk = updater.download(release, new UpdateClient.Progress() {
                    @Override public void update(int percent) {
                        onUi(() -> setUpdateStatus("Downloading: " + percent + "%", percent));
                    }
                    @Override public void verifying() {
                        onUi(() -> setUpdateStatus("Verifying update…"));
                    }
                });
                onUi(()->setUpdateStatus("Verified. Requesting installation…"));
                preferences.edit().putString("install_status","Installation requested. Confirm on the watch if asked; then reopen the app.").commit();
                updater.install(apk);
                onUi(()->{downloading=false;setUpdateStatus("Installation requested. Reopen Keep Score after it finishes.");if(updatePanel)showUpdates();});
            }catch(Exception error){onUi(()->{downloading=false;setUpdateStatus("Update stopped: "+error.getMessage());if(updatePanel)showUpdates();});}
        });
    }
    private String safe(Exception error){String message=error.getMessage();return message==null?error.getClass().getSimpleName():ui.status(message);}
    private void onUi(Runnable work){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())work.run();});}
    private void setUpdateStatus(String value) { setUpdateStatus(value, -1); }
    private void setUpdateStatus(String value, int percent) {
        updateStatus = value;
        updatePercent = percent;
        preferences.edit().putString("update_status", value).apply();
        renderUpdateStatus();
    }
    private void renderUpdateStatus() {
        if (!updatePanel || updateBar == null) return;
        updateText.setText(ui.status(updateStatus));
        boolean showBar = downloading && updatePercent >= 0;
        updateBar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        updateSpinner.setVisibility(checking || downloading && !showBar ? View.VISIBLE : View.GONE);
        if (showBar) updateBar.setProgress(updatePercent);
    }
    private void allowUpdates(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        try{
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
            startActivity(settings);
        }catch(Exception absent){showPermissionHelp();}
    }
    private void showPermissionHelp(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        LinearLayout help=panel("Direct updates");
        paragraph(help,tr("This watch hides the install permission screen. A paired phone's ADB tool may enable it once. No PC is needed.\n\nCommand:\nappops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow\n\nIf this firmware cannot enable it, use the phone installer fallback."));
        primaryButton(help,tr("Copy command"),()->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Update setup","appops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow"));
        });navigationButton(help,tr("Phone install help"),this::phoneHelp);backButton(help,"Back to App updates",this::showUpdates);
    }
    private void phoneHelp(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        LinearLayout help=panel("Update by phone");
        paragraph(help,tr("Open the Keep Score release page on your Android phone. Download the latest watch APK.\n\nIn Wear Installer 2, connect to this watch, choose Custom APK and install the download.\n\nUse the same Wi-Fi network or your phone's hotspot. Your score is kept when installing an update over the existing app.\n\nRelease address:\n")+BuildConfig.SITE_URL);
        backButton(help,"Back to App updates",this::showUpdates);
    }
    private void showPlayUpdates(){
        LinearLayout content=panel("App updates");updatePanel=true;
        updateText=paragraph(content,tr("Installed: v")+BuildConfig.VERSION_NAME+tr("\n\nUpdates are managed by Google Play. Open Keep Score in the Play Store on your watch to check for an update."));
        primaryButton(content,tr("Open Google Play"),this::openPlayStore);
        backButton(content,"Back to Settings",this::showSettings);
    }
    private void openPlayStore(){
        Intent store=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+getPackageName())).setPackage("com.android.vending");
        try{startActivity(store);}catch(android.content.ActivityNotFoundException missing){
            message(tr("Google Play unavailable"),tr("Open the Play Store on your watch and search for Keep Score. Your match and saved results are kept."),this::showPlayUpdates);
        }
    }
    private void showDiagnostics(){
        LinearLayout details=panel("Diagnostics");
        String text=diagnosticText();
        paragraph(details,text);primaryNavigationButton(details,tr("Send to phone"),this::showDiagnosticTransfer);
        secondaryButton(details,tr("Copy details"),()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Keep Score diagnostics",text)));
        backButton(details,"Back to Settings",this::showSettings);
    }
    private String diagnosticText(){
        String trace=AppDiagnostics.recentTrace(preferences.getString("lifecycle_trace",""));
        String crash=preferences.getString("last_crash",tr("No recorded Java crash"));
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
        String text="Keep Score v"+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\n"+java.time.LocalDateTime.now()+"\nAndroid API "+Build.VERSION.SDK_INT
            +tr("\nDevice: ")+Build.MANUFACTURER+" "+Build.MODEL+tr("\nDirect install allowed: ")+getPackageManager().canRequestPackageInstalls()
            +tr("\nNotifications enabled: ")+getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()
            +tr("\nAmbient support: ")+(ambientObserver!=null)+tr("; resumed: ")+resumed+tr("; panel open: ")+(panel!=null)
            +tr("\nPage: ")+panelPage+tr("; scoreboard flag: ")+((getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)+tr("; screen interactive: ")+power.isInteractive()
            +tr("\nPower saver: ")+power.isPowerSaveMode()+tr("; device idle: ")+power.isDeviceIdleMode()+tr("; battery exempt: ")+power.isIgnoringBatteryOptimizations(getPackageName())
            +tr("\nBattery (whole watch): ")+BatterySnapshot.describe(this)+tr("; session active: ")+sessionOngoing()
            +tr("\nScore: ")+book.current().game().blue()+"–"+book.current().game().red()+tr("; sets ")+book.current().gamesWon(0)+"–"+book.current().gamesWon(1)
            +tr("\nBest of ")+book.current().bestOf()+tr("; saved results ")+book.count()
            +tr("\nPaused / waiting to resume: ")+needsResume()+tr("; ambient: ")+ambient+tr("; keep awake setting: ")+preferences.getBoolean("match_keep_awake_v3",true)
            +"\n\n"+ui.status(updateStatus)+"\n\n"+updateDetails()+"\n\n"+tr(preferences.getString("diagnostic",""))
            +tr("\n\nLast crash:\n")+crash+tr("\n\nProcess exit reasons:\n")+AppDiagnostics.recentExits(this,false)+tr("\n\nLifecycle trace:\n")+trace;
        return text;
    }
    private String diagnosticQrState() {
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        android.os.BatteryManager battery = getSystemService(android.os.BatteryManager.class);
        int level = battery == null ? -1 : battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (level < 0 || level > 100) level = -1;
        // Fragment schema 1. The phone expands these flags into the watch language.
        String bits = DiagnosticTransfer.flags(getPackageManager().canRequestPackageInstalls(),
            getSystemService(android.app.NotificationManager.class).areNotificationsEnabled(),
            ambientObserver != null, resumed, panel != null,
            (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0,
            power.isInteractive(), power.isPowerSaveMode(), power.isDeviceIdleMode(),
            power.isIgnoringBatteryOptimizations(getPackageName()), sessionOngoing(), needsResume(),
            ambient, preferences.getBoolean("match_keep_awake_v3", true), battery != null && battery.isCharging());
        MatchState match = book.current();
        return "1." + ui.code + "." + bits + "." + level + "." + match.game().blue() + "." + match.game().red()
            + "." + match.gamesWon(0) + "." + match.gamesWon(1) + "." + match.bestOf() + "." + book.count();
    }

    private void showDiagnosticTransfer(){
        try {
            String device = DiagnosticTransfer.excerpt(Build.MANUFACTURER + " " + Build.MODEL, 32);
            String header = "Keep Score v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + java.time.LocalDateTime.now().withNano(0) + "\n" + device + " · API " + Build.VERSION.SDK_INT;
            String trace = AppDiagnostics.recentTrace(preferences.getString("lifecycle_trace", ""));
            String crash = preferences.getString("last_crash", "");
            if (!crash.isEmpty()) {
                // The saved first two lines identify the time, thread and exception.
                String[] lines = crash.split("\n", 3);
                crash = tr("Last crash:") + " " + DiagnosticTransfer.excerpt(lines[0], 48);
                if (lines.length > 1) crash += " " + DiagnosticTransfer.excerpt(lines[1], 64);
            }
            String exit = AppDiagnostics.recentExits(this, true);
            if (!exit.isEmpty()) exit = tr("Process exit reasons:") + " " + exit;
            String note = DiagnosticTransfer.excerpt(tr(preferences.getString("diagnostic", "")), 100);
            String link = DiagnosticTransfer.watchLink(BuildConfig.SITE_URL, header, diagnosticQrState(), trace, crash, note, exit);
            showQr(link, tr("Scan with phone"), this::showDiagnostics);
        }
        catch(Exception error){message(tr("Diagnostic transfer"),safe(error),this::showDiagnostics);}
    }
    private void showQr(String link,String title,Runnable back){
        showQr(link,title,back,"Back to Diagnostics");
    }
    void showQr(String link,String title,Runnable back,String backLabel){
        showQr(link,title,back,backLabel,true);
    }
    void showQr(String link,String title,Runnable back,String backLabel,boolean phoneNfc){
        try{
            java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L);
            hints.put(com.google.zxing.EncodeHintType.MARGIN,4);hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");
            boolean diagnostic = "Back to Diagnostics".equals(backLabel);
            android.util.DisplayMetrics screen=getResources().getDisplayMetrics();
            boolean round=getResources().getConfiguration().isScreenRound();
            float fraction = diagnostic ? (round ? .69f : .78f) : .64f;
            int size = Math.round(Math.min(screen.widthPixels, screen.heightPixels) * fraction);
            // Grow the diagnostic symbol to its frame with a sharp four-module quiet zone.
            int requested = diagnostic ? 0 : size;
            com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,com.google.zxing.BarcodeFormat.QR_CODE,requested,requested,hints);
            int width=matrix.getWidth(),height=matrix.getHeight();
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.RGB_565);
            int[] row=new int[width];
            for(int y=0;y<height;y++){
                for(int x=0;x<width;x++)row[x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                bitmap.setPixels(row,0,width,0,y,width,1);
            }
            if (diagnostic) bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, false);
            bitmap.setDensity(android.graphics.Bitmap.DENSITY_NONE);
            LinearLayout page=panel(title,null,PanelTitle.CUSTOM);
            panelPage=backLabel.equals("Back to Diagnostics")?"Diagnostic QR":backLabel.equals("Back to Live scoreboard")?"Audience QR":"Phone QR";
            ImageView qr=new ImageView(this);
            BitmapDrawable code=new BitmapDrawable(getResources(),bitmap);
            code.setFilterBitmap(false);
            qr.setImageDrawable(code);
            qr.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            qr.setContentDescription(tr(title));
            int titleWidth=screen.widthPixels-panelScroll.getPaddingLeft()-panelScroll.getPaddingRight();
            if(round)titleWidth=Math.min(titleWidth,Math.round(screen.widthPixels*.62f));
            TextView heading=panelHeading(page,tr(title),true);
            heading.setLayoutParams(new LinearLayout.LayoutParams(titleWidth,ViewGroup.LayoutParams.WRAP_CONTENT));
            heading.measure(View.MeasureSpec.makeMeasureSpec(titleWidth,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int centerY=screen.heightPixels/2;
            int bottomLimit=screen.heightPixels;
            if(round){
                double radius=Math.min(screen.widthPixels,screen.heightPixels)/2.0;
                double halfCode=size/2.0;
                bottomLimit=centerY+(int)Math.sqrt(radius*radius-halfCode*halfCode)-dp(2);
            }
            int top=panelScroll.getPaddingTop();
            boolean titleFits=top+heading.getMeasuredHeight()+size<=bottomLimit;
            // Keep all four corners scannable when a large title needs a second line.
            int used=top+(titleFits?heading.getMeasuredHeight():0);
            LinearLayout.LayoutParams frame=new LinearLayout.LayoutParams(size,size);
            frame.topMargin=Math.max(0,centerY-size/2-used);
            frame.bottomMargin=dp(4);
            page.addView(qr,titleFits?1:0,frame);
            if(phoneNfc&&nfc.canSend())navigationButton(page,tr("Share page via NFC"),
                ()->nfc.shareLink(link,()->showQr(link,title,back,backLabel,phoneNfc),"Back to QR"));
            backButton(page,backLabel,back);
            final Window qrWindow=panel.getWindow();
            if(resumed)qrWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            qr.postDelayed(()->qrWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON),60000L);
        }catch(Exception error){message("QR unavailable",safe(error),back);}
    }
    void keepNfcScreenAwake(boolean awake) {
        if (panel == null) return;
        if (awake && resumed) panel.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else panel.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private LinearLayout panel(String title){
        return panel(title,null);
    }
    private LinearLayout panel(String title,String scrollKey){return panel(title,scrollKey,PanelTitle.STANDARD);}
    private LinearLayout panel(String title,String scrollKey,PanelTitle titleKind){return panel(title,scrollKey,titleKind,null);}
    private LinearLayout panel(String title,String scrollKey,MenuSection section){return panel(title,scrollKey,PanelTitle.STANDARD,section);}
    private LinearLayout panel(String title,String scrollKey,PanelTitle titleKind,MenuSection section){
        String route=scrollKey==null?"title:"+title:scrollKey;
        if(route.startsWith("results:"))route="results";
        if(route.startsWith("current-match:"))route="current-match";
        String pathTitle=title.equals("Menu")?MatchFormat.name(selectedSport):route.startsWith("result:")?"Match result":title;
        if(route.startsWith("set-points:"))pathTitle="Losing score";
        if(route.startsWith("phone-transfer:"))pathTitle=phoneTitle();
        boolean homeRoute=false;
        for(MenuLocation step=location;step!=null;step=step.parent)if(step.key.equals("home")){homeRoute=true;break;}
        MenuLocation next=MenuLocation.open(panel==null||route.equals("home")||route.equals("sports")&&!homeRoute?null:location,route,pathTitle,section);
        closePanel();
        location=next;
        panel=new Dialog(this,R.style.WatchPanel){
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)reviewInteraction();
                return super.dispatchTouchEvent(event);
            }
            @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_SCROLL)reviewInteraction();
                return super.dispatchGenericMotionEvent(event);
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event){
                if(event.getAction()==KeyEvent.ACTION_DOWN)reviewInteraction();
                return super.dispatchKeyEvent(event);
            }
        };panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(MENU_SURFACE);scroll.setClipToPadding(false);
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();boolean round=getResources().getConfiguration().isScreenRound();
        int horizontal=round?Math.round(metrics.widthPixels*.095f):dp(12),top=round?Math.round(metrics.heightPixels*.105f):dp(14);
        if(round&&route.equals("home"))top=0;
        scroll.setPadding(horizontal,top,horizontal,round?Math.round(metrics.heightPixels*.14f):dp(18));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        boolean customTitle=titleKind==PanelTitle.CUSTOM;
        if(!customTitle){
            locationHeader(content,sharedArea?null:selectedSport,titleKind==PanelTitle.PLAYER_NAME?title:tr(title),route.equals("results")&&round);
        }
        content.setAccessibilityPaneTitle(titleKind==PanelTitle.PLAYER_NAME?title:tr(title));
        panelShortcut=!customTitle&&(titleKind==PanelTitle.PLAYER_NAME||!title.equals("Menu")&&!title.equals("Choose sport")&&!title.endsWith("?"))&&book.current().format().sport.equals(selectedSport)&&(sessionOngoing()||needsResume());
        // When a match finishes, start the idle menu at its primary action.
        final String positionKey = scrollKey != null && (route.equals("sports") || route.startsWith("sport-menu:"))
                ? scrollKey + (sportLocked() ? ":playing" : unsavedFinish() ? ":pending-save" : ":idle") : scrollKey;
        panelScroll=scroll;panelScrollKey=positionKey;
        panelPage=title;panel.setContentView(swipeContainer(scroll,panel));panel.show();
        panel.getWindow().setBackgroundDrawable(new ColorDrawable(MENU_SURFACE));
        panel.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        trackPanelDismiss();
        bindBack(this::closePanel);
        if(positionKey!=null){final int position=scrollPositions.containsKey(positionKey)?scrollPositions.get(positionKey):0;scroll.post(()->scroll.scrollTo(0,position));}
        updateDisplay();
        return content;
    }
    private TextView panelHeading(LinearLayout content,String title,boolean compact){
        TextView heading=paragraph(content,title);
        heading.setTextSize(compact?14:18);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setTextColor(location.section.accent);
        heading.setPadding(0,dp(compact?0:3),0,dp(6));
        heading.setAccessibilityHeading(true);
        return heading;
    }
    private void addHomeBrand(LinearLayout header){
        LinearLayout brand=new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        header.addView(brand,new LinearLayout.LayoutParams(-1,-2));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo,new LinearLayout.LayoutParams(dp(20),dp(20)));

        TextView name=new TextView(this);
        name.setText(R.string.app_name);
        name.setTextSize(15);
        name.setTextColor(Color.WHITE);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setPadding(dp(3),0,0,0);
        brand.addView(name,new LinearLayout.LayoutParams(-2,-2));

        if(roundMenu()){
            float labelWidth=name.getPaint().measureText(name.getText().toString());
            float width=dp(20)+name.getPaddingLeft()+labelWidth;
            if(width>menuWidth()){
                brand.setOrientation(LinearLayout.VERTICAL);
                name.setPadding(0,0,0,0);
                width=Math.min(labelWidth,menuWidth());
            }
            // At this inset the circle is wide enough for both ends of the wordmark.
            brand.setPadding(0,roundInsetForWidth(width),0,dp(3));
        }
    }
    private LinearLayout locationHeader(LinearLayout content,String sport,String title,boolean compact){
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);header.setGravity(Gravity.CENTER);
        header.setMinimumHeight(dp(48));
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
        if(title==null){
            addHomeBrand(header);
            title=getString(R.string.app_name)+". "+tr("Home");
        }else{
            sportBadge(header,sport);
            panelHeading(header,title,compact);
        }
        bindMenuPath(header,header,title);
        return header;
    }
    private void bindMenuPath(LinearLayout content,View heading,String title){
        String path=location.path(this::tr);
        TextView trail=paragraph(content,path);
        trail.setTextSize(12);trail.setVisibility(View.GONE);
        heading.setFocusable(true);
        heading.setContentDescription(title+". "+tr("Show menu path"));
        heading.setOnClickListener(v->{
            boolean reveal=trail.getVisibility()!=View.VISIBLE;
            trail.setVisibility(reveal?View.VISIBLE:View.GONE);
            heading.setContentDescription(title+". "+(reveal?path+". "+tr("Hide menu path"):tr("Show menu path")));
            if(reveal)heading.announceForAccessibility(path);
        });
    }
    private void trackPanelDismiss(){
        final Dialog shown=panel;
        Runnable releaseBack=registerPanelBack(shown);
        panel.setOnDismissListener(d->{releaseBack.run();if(panel==shown){if(nfc!=null)nfc.clear();rememberPanelScroll();resetUpdatePanel();panel=null;panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";updateDisplay();}});
    }
    private Runnable registerPanelBack(Dialog shown){
        if(Build.VERSION.SDK_INT<33)return ()->{};
        android.window.OnBackInvokedCallback back=()->{if(panel==shown && shown.isShowing())shown.cancel();};
        android.window.OnBackInvokedDispatcher dialogBack=shown.getOnBackInvokedDispatcher();
        android.window.OnBackInvokedDispatcher activityBack=getOnBackInvokedDispatcher();
        dialogBack.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);
        activityBack.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);
        return ()->{dialogBack.unregisterOnBackInvokedCallback(back);activityBack.unregisterOnBackInvokedCallback(back);};
    }
    private View swipeContainer(View child,Dialog shown){
        SwipeDismissFrameLayout frame=new SwipeDismissFrameLayout(this);frame.setSwipeable(true);frame.addView(child,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addCallback(new SwipeDismissFrameLayout.Callback(){@Override public void onDismissed(SwipeDismissFrameLayout layout){if(panel==shown)shown.cancel();}});return frame;
    }
    private void bindBack(Runnable action){
        final Dialog shown=panel;
        shown.setOnCancelListener(d->{if(!isFinishing()&&!isDestroyed()&&panel==shown)action.run();});
        shown.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())shown.cancel();return true;});
    }
    @Override public void onBackPressed(){if(panel!=null)panel.cancel();else super.onBackPressed();}
    private void rememberPanelScroll(){
        if(panelScrollKey==null||panelScroll==null)return;
        scrollPositions.put(panelScrollKey,panelScroll.getScrollY());
        if(scrollPositions.size()>64)scrollPositions.remove(scrollPositions.keySet().iterator().next());
    }
    TextView paragraph(LinearLayout content,String text){
        TextView view=new TextView(this);view.setText(text);view.setTextColor(0xffd7e2f0);view.setTextSize(13);
        view.setGravity(Gravity.CENTER);view.setPadding(0,dp(5),0,dp(9));
        content.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return view;
    }
    void section(LinearLayout content,String key){
        String title=tr(key);
        TextView label=paragraph(content,title);
        label.setTextSize(12);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        label.setTextColor(location.section.accent);
        label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        label.setPadding(dp(10),dp(9),dp(10),dp(4));
        label.setContentDescription(title);
        label.setAccessibilityHeading(true);
    }
    EditText menuInput(LinearLayout content,String hint,String label,int type){
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setInputType(type);
        field.setTextSize(15);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0xffa7b6c9);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setMinHeight(dp(48));
        field.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(MENU_CARD_SURFACE);
        shape.setCornerRadius(dp(ROW_RADIUS_DP));
        shape.setStroke(dp(1),new ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{0xffcccccc,MENU_CONTROL_BORDER}));
        field.setBackground(shape);
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        position.bottomMargin=dp(ROW_GAP_DP);
        content.addView(field,position);
        return field;
    }
    private void sportBadge(LinearLayout content,String sport){
        TextView badge=paragraph(content,sport==null?getString(R.string.app_name):tr(MatchFormat.name(sport)));badge.setTextSize(12);badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);badge.setTextColor(sport==null?0xffd3d3d3:SportIcons.accent(sport));badge.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(12));shape.setColor(sport==null?MENU_CARD_SURFACE:(SportIcons.accent(sport)&0x00ffffff)|0x18000000);badge.setBackground(shape);
        if(sport!=null){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));badge.setCompoundDrawables(icon,null,null,null);badge.setCompoundDrawablePadding(dp(5));}
        badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));badge.setContentDescription(sport==null?tr("Keep Score, shared across all sports"):tr("Current sport: ")+tr(MatchFormat.name(sport)));
    }
    private void sportButton(Button button,String sport){
        int accent=SportIcons.accent(sport);GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(ROW_RADIUS_DP));shape.setColor((accent&0x00ffffff)|0x18000000);shape.setStroke(dp(1),(accent&0x00ffffff)|0x66000000);
        ColorStateList ink=MenuSection.text(accent,0xff687589);
        Drawable arrow=button.getCompoundDrawablesRelative()[2];
        arrow.setTintList(ink);
        Drawable icon=new SportIcons(sport,dp(22));
        icon.setBounds(0,0,dp(22),dp(22));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));
        button.setTextColor(ink);
        button.setCompoundDrawablesRelative(icon,null,arrow,null);
        button.setCompoundDrawablePadding(dp(8));
    }
    Button choiceButton(LinearLayout content,String text,boolean selected,Runnable action){
        return markChoice(styledButton(content,text,action,ButtonKind.CHOICE),selected);
    }
    private Button markChoice(Button option,boolean selected){
        CharSequence label=option.getContentDescription()==null?option.getText():option.getContentDescription();
        option.setSelected(selected);
        option.setContentDescription(label+(selected?tr(", selected"):tr(", select")));
        if(selected)option.setText(new android.text.SpannableStringBuilder("✓ ").append(option.getText()));
        return option;
    }
    private Button playerChoice(LinearLayout page,String name,boolean selected,Runnable action){
        return withPersonIcon(choiceButton(page,name,selected,action),MENU_CONTROL_TEXT);
    }
    private Button savedPlayerChoice(LinearLayout page,String name,boolean selected,Runnable action){
        return markChoice(styledButton(page,name,action,ButtonKind.PLAYER_CHOICE),selected);
    }
    Button primaryButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.PRIMARY);}
    Button primaryNavigationButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.PRIMARY_LINK);}
    Button secondaryButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.SECONDARY);}
    private Button homeTile(LinearLayout content,MenuSection section,String text,Runnable action){
        return styledButton(content,text,action,ButtonKind.TILE,section);
    }
    Button navigationButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.NAVIGATION);}
    private Button personButton(LinearLayout content,String text,Runnable action){return withPersonIcon(navigationButton(content,text,action),MENU_CONTROL_TEXT);}
    private Button withPersonIcon(Button button,ColorStateList ink){
        Drawable icon=getDrawable(R.drawable.ic_person).mutate();
        icon.setTintList(ink);
        icon.setBounds(0,0,dp(18),dp(18));
        button.setCompoundDrawablesRelative(icon,null,button.getCompoundDrawablesRelative()[2],null);
        return button;
    }
    Button deleteButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.DELETE);}
    Button backButton(LinearLayout content,String text,Runnable action){
        if(panelShortcut){panelShortcut=false;styledButton(content,tr("Scoreboard"),this::closePanel,ButtonKind.BACK);}
        MenuLocation here=location;
        Runnable back=()->{location=here==null?null:here.parent;action.run();};
        bindBack(back);return styledButton(content,tr(text),back,ButtonKind.BACK);
    }
    private Button styledButton(LinearLayout content,String text,Runnable action,ButtonKind kind){
        return styledButton(content,text,action,kind,null);
    }
    private Button styledButton(LinearLayout content,String text,Runnable action,ButtonKind kind,MenuSection tileSection){
        boolean back=kind==ButtonKind.BACK;
        boolean primary=kind==ButtonKind.PRIMARY||kind==ButtonKind.PRIMARY_LINK;
        boolean danger=kind==ButtonKind.DELETE;
        boolean tool=kind==ButtonKind.RESULT_TOOL;
        boolean player=kind==ButtonKind.PLAYER_LINK||kind==ButtonKind.PLAYER_CHOICE;
        boolean choice=kind==ButtonKind.CHOICE||kind==ButtonKind.PLAYER_CHOICE;
        boolean submenu=kind==ButtonKind.NAVIGATION||kind==ButtonKind.PRIMARY_LINK||kind==ButtonKind.PLAYER_LINK||tool;
        boolean filled=primary||danger||kind==ButtonKind.TILE;
        ColorStateList fill=tileSection!=null?tileSection.fill:primary?PRIMARY_FILL:danger?DANGER_FILL:MENU_CONTROL_FILL;
        ColorStateList ink=primary?PRIMARY_TEXT:filled?FILLED_TEXT:MENU_CONTROL_TEXT;
        Button button=new Button(this);
        button.setText(text);
        button.setTextSize(player?15:14);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(10),dp(6),dp(10),dp(6));
        button.setBackgroundTintList(null);
        GradientDrawable shape=new GradientDrawable();
        shape.setCornerRadius(dp(ROW_RADIUS_DP));
        shape.setColor(fill);
        if(back)shape.setStroke(dp(1),MENU_CONTROL_BORDER);
        if(player){
            int[][] states={{android.R.attr.state_selected},{}};
            MenuSection section=location.section;
            shape.setColor(new ColorStateList(states,new int[]{section.selectedPlayerRow,section.playerRow}));
            shape.setStroke(dp(1),new ColorStateList(states,new int[]{section.accent,Color.TRANSPARENT}));
            button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        }else if(tool||choice){
            int[][] states={{android.R.attr.state_selected},{}};
            shape.setColor(new ColorStateList(states,new int[]{0xff393939,tool?MENU_CARD_SURFACE:Color.TRANSPARENT}));
            shape.setStroke(dp(1),new ColorStateList(states,new int[]{0xffbdbdbd,tool?MENU_CONTROL_BORDER:Color.TRANSPARENT}));
            if(tool)button.setPadding(dp(6),dp(4),dp(6),dp(4));
        }
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));
        if(filled){
            button.setBackgroundTintList(fill);
            button.setBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
        }
        button.setTextColor(ink);
        if(submenu||choice){
            button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            if(submenu){
                Drawable arrow=getDrawable(R.drawable.ic_navigation_arrow).mutate();
                arrow.setTintList(ink);
                int size=dp(tool?12:16);
                arrow.setBounds(0,0,size,size);
                button.setCompoundDrawablesRelative(null,null,arrow,null);
            }
            button.setCompoundDrawablePadding(dp(tool?3:6));
        }
        if(player)withPersonIcon(button,MenuSection.text(location.section.accent,0xff898989));
        button.setOnClickListener(v->{reviewInteraction();action.run();});
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        position.topMargin=back?dp(3):0;
        position.bottomMargin=dp(ROW_GAP_DP);
        content.addView(button,position);
        return button;
    }
    private String resultOutcome(MatchResult result){
        return result.winner()==0?ui.pick("Won","Gewonnen","Victoire","Victoria"):ui.pick("Lost","Verloren","Défaite","Derrota");
    }
    private LinearLayout resultSurface(LinearLayout content,MatchResult result,boolean interactive){
        boolean won=result.winner()==0;
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int inset=dp(interactive?8:10);
        card.setPadding(inset,inset,inset,inset);
        GradientDrawable shape=new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(won?0xff103b68:0xff551c30);
        shape.setStroke(dp(2),won?0xff2489f5:0xfff34b65);
        card.setBackground(interactive?new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null):shape);
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        position.bottomMargin=dp(interactive?6:12);
        content.addView(card,position);
        return card;
    }
    private void historyCard(LinearLayout content,MatchResult result,int page){
        LinearLayout card=resultSurface(content,result,true);
        String tally=result.gamesWon(0)+"–"+result.gamesWon(1);
        String outcome=resultOutcome(result);
        SpannableString standing=new SpannableString(tally+"  "+outcome);
        standing.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),0,tally.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        standing.setSpan(new RelativeSizeSpan(.75f),tally.length()+2,standing.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String players=result.format.doubles?teamName(result.id,0)+tr(" vs ")+teamName(result.id,1):tr("vs ")+opponentName(result.id);
        String reference=ui.date(ResultFilter.date(result));
        if(result.startedAt>0)reference+=" · "+result.heading();
        if(result.retired())reference+=" · "+tr("Retirement");
        TextView score=paragraph(card,"");
        score.setText(standing);
        score.setPadding(0,0,0,dp(1));score.setTextSize(16);score.setTextColor(Color.WHITE);
        TextView names=paragraph(card,players);
        names.setPadding(0,0,0,dp(2));
        TextView date=paragraph(card,reference);
        date.setPadding(0,0,0,0);date.setTextSize(12);
        for(int i=0;i<card.getChildCount();i++)card.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        String decision=result.retired()?ui.scoreTerms(result.outcomeText()):outcome;
        card.setContentDescription(ui.scoreTerms(result.title())+". "+tally+". "+decision+". "+players+tr(". Open match result."));
        card.setFocusable(true);
        card.setOnClickListener(v->{reviewInteraction();showResult(result.id,page);});
    }
    private void resultDetailCard(LinearLayout content,MatchResult result){
        LinearLayout card=resultSurface(content,result,false);
        paragraph(card,tr(result.format.name())+" · "+tr(result.format.mode())+"\n"+(result.format.doubles?teamName(result.id,0)+tr(" vs ")+teamName(result.id,1):tr("vs ")+opponentName(result.id)));
        TextView score=paragraph(card,result.format.doubles?tr("Our team ")+result.gamesWon(0)+"–"+result.gamesWon(1)+tr(" Opponents"):ownName()+" "+result.gamesWon(0)+"–"+result.gamesWon(1)+" "+opponentName(result.id));
        score.setTextSize(17);score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);score.setTextColor(Color.WHITE);
        TextView details=paragraph(card,tr("Best of ")+result.bestOf+" · "+ui.scoreTerms(result.outcomeText())+"\n"+ui.scoreTerms(result.timingText())+tr("\nDuration: ")+ui.scoreTerms(result.durationText())+tr("\nMatch ")+result.number+"\n\n"+ui.scoreTerms(result.gamesText()));
        details.setTextSize(12);details.setTextColor(0xffedf4ff);
    }
    private void resetUpdatePanel(){
        updatePanel=false;
        updateText=null;
        updateBar=null;
        updateSpinner=null;
    }
    void closePanel(){if(nfc!=null)nfc.clear();rememberPanelScroll();if(panel!=null){panel.dismiss();panel=null;}location=null;panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";resetUpdatePanel();updateDisplay();}
}
