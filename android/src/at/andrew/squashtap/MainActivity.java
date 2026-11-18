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
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.NumberPicker;
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
    private String tr(String label){return ui.text(label);}
    private MatchBook book;
    private OpponentDirectory opponents;
    private String filterOpponent;
    private LocalDate filterFrom,filterTo;
    private EditText nameInput;
    private TransferClient.Session transfer;
    private boolean transferBusy;
    private ScoreView counter;
    private MatchRooms rooms;
    private UpdateClient updater;
    private LiveScoreClient live;private TextView liveText;private String livePanelState="";
    private UpdatePolicy latest;
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private boolean checking,downloading;
    private String updateStatus="Tap Check for updates to contact the release website.";
    private Dialog panel;
    private TextView updateText;
    private boolean updatePanel;
    private String panelPage="Scoreboard";
    private enum ButtonKind { MATCH, NAVIGATION, PERSON, SETTINGS, DELETE, BACK, SHORTCUT, SCORE_OPPONENT }
    private boolean sharedArea,scoreMenu;
    private boolean panelShortcut;
    private Runnable playersReturn=this::showMenu,settingsReturn=this::showMenu;
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
    private LocalDate manualDate;
    private Runnable transferReturn=()->showResults(resultsPage);
    private String transferReturnLabel="Back to Results",transferView="results";
    private boolean transferFiltered;private String transferResultId="";
    private boolean transferSnapshotFiltered,transferSnapshotBackup;
    private ScrollView panelScroll;
    private String panelScrollKey;
    private final java.util.LinkedHashMap<String,Integer> scrollPositions=new java.util.LinkedHashMap<>();
    private boolean resumed,ambient;
    private final LifecycleRegistry lifecycle=new LifecycleRegistry(this);
    private AmbientLifecycleObserver ambientObserver;
    private boolean ambientUnavailable;private String widgetSignature="";
    @Override public Lifecycle getLifecycle(){return lifecycle;}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        ui=WatchLanguage.get(this);
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
        if(rooms.readOnly()){updateDisplay();if(rooms.finalScore())rooms.hub();return;}
        if(!handleWidgetIntent(getIntent())&&Intent.ACTION_MAIN.equals(getIntent().getAction()))showSports();
    }
    private boolean handleWidgetIntent(Intent intent){
        String route=intent==null?null:intent.getStringExtra("keep_score_widget");if(route==null)return false;
        intent.removeExtra("keep_score_widget");
        if(rooms!=null&&rooms.readOnly()){
            if(rooms.finalScore()||rooms.displayed()==null)rooms.hub();else closePanel();
            return true;
        }
        if(route.equals("match")&&hasCurrentMatch()){selectSport(book.current().format().sport);if(needsResume())showMatchControls();else closePanel();}else showSports();return true;
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
                ambient=true;closePanel();counter.ambient(true,details.getDeviceHasLowBitAmbient());updateDisplay();AppDiagnostics.record(MainActivity.this,"Enter ambient");
            }
            @Override public void onExitAmbient(){ambient=false;counter.ambient(false,false);updateDisplay();AppDiagnostics.record(MainActivity.this,"Exit ambient");}
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
        super.onResume();resumed=true;lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);updateDisplay();
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
    @Override protected void onPause(){if(rooms!=null)rooms.pause();AppDiagnostics.record(this,"Activity pause; page="+panelPage+" screenFlag="+((getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)+" ambient="+ambient+" finishing="+isFinishing());persistScore();resumed=false;updateDisplay();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);super.onPause();}
    @Override protected void onStop(){lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);AppDiagnostics.record(this,"Activity stop; finishing="+isFinishing()+" changingConfig="+isChangingConfigurations());super.onStop();}
    @Override protected void onDestroy(){if(rooms!=null)rooms.destroy();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);AppDiagnostics.record(this,"Activity destroy; finishing="+isFinishing());if(live!=null)live.listener(null);tasks.shutdownNow();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(!handleWidgetIntent(intent))closePanel();updateDisplay();AppDiagnostics.record(this,"Returned through match shortcut");}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){super.onRequestPermissionsResult(code,names,grants);if(code==71){AppDiagnostics.record(this,"Notification permission result "+(grants.length>0?grants[0]:-1));updateDisplay();}}
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
            ?book.current().encode()+"|"+ownName()+"|"+ongoing+"|"+paused+"|"+preferences.getString("match_ready_id","")
            :remoteRevision;
        if(!signature.equals(widgetSignature)){widgetSignature=signature;MatchWidgets.update(this);}
        if(awake)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(!resumed&&panel!=null)panel.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent service=new Intent(this,MatchSessionService.class);
        if(!ongoing)stopService(service);
        else if(resumed){try{startForegroundService(service);}catch(Exception error){AppDiagnostics.record(this,"Session start failed: "+safe(error));}}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void action(int target){
        if(rooms!=null && rooms.readOnly()){rooms.hub();return;}
        switch(target){
            case TouchTargets.BLUE:case TouchTargets.RED:
                if(needsResume()){showMatchControls();return;}
                if(book.current().format().pickleball()&&book.current().firstServer(book.current().gameCount()-1)<0){showServing();return;}
                MatchState beforePoint=MatchState.decode(book.current().encode());
                if(book.add(target)){
                    MatchHaptics.play(this,preferences.getString("haptic_mode","milestones"),MatchReminders.milestone(beforePoint,book.current()));
                    if(preferences.getBoolean("match_reminders",false))counter.reminder(MatchReminders.transition(beforePoint,book.current()));
                    if(!sessionOngoing()&&!book.current().complete())setSession(true,false);
                    if(!afterMatchChange())return;
                    if(book.current().complete()){
                        if(book.result(book.current().id())!=null)showResult(book.current().id(),0);
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
        if(rooms!=null && rooms.readOnly()){rooms.hub();return;}
        if(!allowSport(selectedSport))return;
        sharedArea=false;
        LinearLayout menu=panel("Menu");
        boolean here=book.current().format().sport.equals(selectedSport)&&(hasCurrentMatch()||scoreMenu);
        if(here)button(menu,tr("Current match"),this::showMatchControls);
        else{button(menu,ui.format("New {0} match","Neue Partie · {0}","Nouveau match · {0}","Nuevo partido · {0}",tr(MatchFormat.name(selectedSport))),()->showNewMatch(this::showMenu));if(hasCurrentMatch()&&!book.current().complete())navigationButton(menu,tr("Resume ")+tr(book.current().format().name()),this::showMatchControls);}
        sharingButton(menu,MatchRooms.Entry.SPORT);
        navigationButton(menu,tr("Results (")+sportResults(-1).size()+")",()->openResults(this::showMenu,"Back to Menu"));
        navigationButton(menu,tr("Statistics"),this::showStatistics);
        navigationButton(menu,tr("Favourite formats"),()->showPresets(false));
        section(menu,tr("Sport"));
        navigationButton(menu,tr("Scoring rules"),this::showSportRules);
        navigationButton(menu,tr("Change sport"),this::showSports);
        section(menu,"Keep Score");
        personButton(menu,tr("Players"),()->openPlayers(this::showMenu,"Back to Menu"));
        settingsButton(menu,tr("Settings"),()->openSettings(this::showMenu,"Back to Menu"));
        if(here&&!sportMenuFromSelector)backButton(menu,"Back to score",this::closePanel);
        else backButton(menu,"Back to sports",this::showSports);
    }
    private boolean hasCurrentMatch(){return book.current().hasPoints()||book.current().complete()||book.currentStartedAt()>0||sessionOngoing()||needsResume()||book.current().id().equals(preferences.getString("match_ready_id",""));}
    private boolean sportLocked(){return !book.current().complete()&&hasCurrentMatch();}
    private boolean allowSport(String sport){if(rooms!=null&&rooms.readOnly()){rooms.hub();return false;}if(sportLocked()&&!sport.equals(book.current().format().sport)){showSportLocked();return false;}return true;}
    private void showSportLocked(){
        selectedSport=book.current().format().sport;sharedArea=false;LinearLayout content=panel("Match still running");
        paragraph(content,ui.format("Finish or cancel your {0} match before choosing another sport. Pausing keeps the sport locked.","Beende deine Partie ({0}) oder brich sie ab, um die Sportart zu wechseln. Bei einer Pause bleibt sie gesperrt.","Termine ou annule ton match de {0} pour changer de sport. Une pause conserve le sport actuel.","Termina o cancela tu partido de {0} para cambiar de deporte. Una pausa mantiene el deporte actual.",tr(book.current().format().name())));
        button(content,tr("Current match"),this::showMatchControls);deleteButton(content,tr("Cancel match"),this::showCancelMatch);backButton(content,"Back to sports",this::showSports);
    }
    void showMatchControls(){
        if(rooms!=null && rooms.readOnly()){rooms.hub();return;}
        MatchState match=book.current();selectSport(match.format().sport);sharedArea=false;LinearLayout menu=panel("Current match");
        boolean ongoing=sessionOngoing();
        if(!match.complete())button(menu,ongoing?tr("Pause match"):needsResume()?tr("Resume match"):tr("Start match"),()->{setSession(!ongoing,ongoing);closePanel();updateDisplay();});
        if(match.complete()){
            if(book.result(match.id())!=null)button(menu,tr("View result"),()->showResult(match.id(),resultsPage,this::showMatchControls,"Back to Current match"));
            else if(!book.currentDeleted())button(menu,tr("Save match result"),this::saveCurrentResult);
            button(menu,tr("New match"),()->showNewMatch(this::showMatchControls));
            if(book.result(match.id())!=null)navigationButton(menu,tr("Share result card"),()->openPhone("results",false,this::showMatchControls,"Back to Current match",match.id()));
            if(rooms.hasRoom()||hasAudienceLink())sharingButton(menu,MatchRooms.Entry.MATCH);
        }else sharingButton(menu,MatchRooms.Entry.MATCH);
        paragraph(menu,tr(match.format().name())+" · "+ui.rules(match.format()));
        paragraph(menu,(match.complete()?tr("Finished"):ongoing?tr("Playing"):needsResume()?tr("Paused / ready to resume"):tr("Ready to play"))+tr(" · Best of ")+match.bestOf()+"\n"+ownName()+" "+match.gamesWon(0)+"–"+match.gamesWon(1)+" "+opponentName(match.id()));
        if(!match.complete())navigationButton(menu,tr("Enter set score"),this::enterCurrentSet);
        navigationButton(menu,tr("Match details"),this::showCurrentMatch);
        if(!match.complete()){navigationButton(menu,tr("Match options"),this::showMatchOptions);deleteButton(menu,tr("Cancel match"),this::showCancelMatch);}
        backButton(menu,"Back to Menu",this::showMenu);
    }
    private void showMatchOptions(){
        MatchState match=book.current();if(match.complete()){showMatchControls();return;}
        LinearLayout menu=panel("Match options","match-options:"+match.id());
        section(menu,tr("Setup & players"));
        if(match.format().doubles)personButton(menu,tr("Players"),()->showTeam(match.id(),match.format(),this::showMatchOptions));else personButton(menu,tr("Opponent: ")+opponentName(match.id()),()->showOpponentPicker(match.id(),false,0,this::showMatchOptions));
        navigationButton(menu,tr("Serving indicator"),this::showServing);
        navigationButton(menu,tr("Match length: Best of ")+match.bestOf(),this::showMatchFormat);
        paragraph(menu,tr("Scoring: ")+ui.rules(match.format())+tr("\nChoose different scoring rules when creating a new match."));
        section(menu,tr("Finish or replace"));
        navigationButton(menu,tr("New match"),()->showNewMatch(this::showMatchOptions));
        navigationButton(menu,tr("Save retirement result"),this::showEndMatch);
        paragraph(menu,tr("Retirement saves a win/loss. Cancel match discards this match without a result."));
        deleteButton(menu,tr("Cancel match"),()->showCancelMatch(this::showMatchOptions));
        backButton(menu,"Back to Current match",this::showMatchControls);
    }
    private void showSettings(){
        LinearLayout menu=panel("Settings","settings");paragraph(menu,"Keep Score v"+BuildConfig.VERSION_NAME);
        settingsButton(menu,tr("Language")+" · "+ui.locale.getDisplayLanguage(ui.locale),()->chooseLanguage(this::showSettings,"Back to Settings"));
        section(menu,tr("Display"));
        settingsButton(menu,tr("Colour scheme: ")+tr(ScorePalette.get(preferences.getString("score_palette","classic")).name),this::choosePalette);
        settingsButton(menu,tr("Brightness: watch system"),this::showBrightness);
        settingsButton(menu,tr("Keep screen on: ")+(preferences.getBoolean("match_keep_awake_v3",true)?tr("ON"):tr("OFF")),this::chooseScreenPolicy);
        section(menu,tr("Feedback"));
        settingsButton(menu,tr("Vibrations: ")+vibrationLabels()[vibrationIndex()],this::chooseVibrations);
        settingsButton(menu,tr("Court and interval reminders: ")+(preferences.getBoolean("match_reminders",false)?tr("On"):tr("Off")),this::chooseReminders);
        section(menu,tr("Data"));settingsButton(menu,tr("Backup & restore"),this::showBackup);
        section(menu,tr("Watch shortcuts"));
        settingsButton(menu,tr("Watch tile & face score"),()->{LinearLayout c=panel("Quick access");paragraph(c,tr("Add the Keep Score tile from your watch’s tile picker. On a compatible watch face, add Keep Score · Match score as a complication. Tapping either opens the match without changing its score or pause state."));backButton(c,"Back to Settings",this::showSettings);});
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            settingsButton(menu,tr("Enable match shortcut"),()->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71));
        section(menu,tr("App"));settingsButton(menu,tr("Check for updates"),()->{showUpdates();checkUpdates();});
        settingsButton(menu,tr("Diagnostics"),this::showDiagnostics);backButton(menu,settingsReturnLabel,settingsReturn);
    }
    void roomLanguage(Runnable back){chooseLanguage(back,"Back");}
    private void chooseLanguage(Runnable back,String backLabel){
        LinearLayout content=panel("Language","language");
        String selected=preferences.getString(WatchLanguage.PREFERENCE,"auto");
        String[] names={tr("Automatic (watch)"),"English","Deutsch","Français","Español"};
        for(int i=0;i<WatchLanguage.CODES.length;i++){
            final String code=WatchLanguage.CODES[i];
            choiceButton(content,names[i],code.equals(selected),()->{
                if(code.equals(selected))return;
                if(!preferences.edit().putString(WatchLanguage.PREFERENCE,code).commit()){
                    message(tr("Setting not saved"),tr("The watch could not save this change."),()->chooseLanguage(back,backLabel));
                    return;
                }
                ui=WatchLanguage.get(this);
                counter.language(ui);
                widgetSignature="";
                updateDisplay();
                chooseLanguage(back,backLabel);
            });
        }
        backButton(content,backLabel,back);
    }
    private String[] vibrationLabels(){return new String[]{tr("Off"),tr("Point confirmation"),tr("Points and milestones")};}
    private int vibrationIndex(){String value=preferences.getString("haptic_mode","milestones");return value.equals("off")?0:value.equals("points")?1:2;}
    private void chooseVibrations(){settingChoices(tr("Vibrations"),tr("Distinct patterns identify points, games, sets and match completion."),vibrationLabels(),vibrationIndex(),v->preferences.edit().putString("haptic_mode",new String[]{"off",tr("points"),"milestones"}[v]).commit());}
    private void chooseReminders(){settingChoices(tr("Match reminders"),tr("Brief non-blocking reminders. Players remain responsible for court changes and breaks."),new String[]{tr("Off"),tr("On")},preferences.getBoolean("match_reminders",false)?1:0,v->preferences.edit().putBoolean("match_reminders",v==1).commit());}
    private void settingChoices(String title,String help,String[] labels,int selected,java.util.function.IntPredicate save){
        LinearLayout c=panel(title,"settings:"+title);
        for(int i=0;i<labels.length;i++){final int value=i;choiceButton(c,labels[i],value==selected,()->{if(value==selected)return;if(save.test(value)){updateDisplay();settingChoices(title,help,labels,value,save);}else message(tr("Setting not saved"),tr("The watch could not save this change."),()->settingChoices(title,help,labels,selected,save));});}
        paragraph(c,help+tr("\n\nChanges save immediately."));backButton(c,"Back to Settings",this::showSettings);
    }
    private void openSettings(Runnable back,String label){sharedArea=true;settingsReturn=back;settingsReturnLabel=label;showSettings();}
    private void openPlayers(Runnable back,String label){sharedArea=true;playersReturn=back;playersReturnLabel=label;showOpponents(0);}
    private void chooseScreenPolicy(){settingChoices(tr("Screen during play"),tr("Applies only to the visible scoreboard during active play. Menus and paused matches allow normal sleep."),new String[]{tr("Keep screen on"),tr("Allow screen to sleep")},preferences.getBoolean("match_keep_awake_v3",true)?0:1,v->preferences.edit().putBoolean("match_keep_awake_v3",v==0).commit());}
    private void choosePalette(){
        LinearLayout c=panel("Colour scheme","palette");String selected=preferences.getString("score_palette","classic");
        for(String id:ScorePalette.IDS){ScorePalette p=ScorePalette.get(id);choiceButton(c,tr(p.name),id.equals(selected),()->{if(id.equals(selected))return;if(preferences.edit().putString("score_palette",id).commit()){updateDisplay();choosePalette();}else message(tr("Colour not saved"),tr("Try choosing the scheme again."),this::choosePalette);});
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,0,0,dp(8));for(int side=0;side<2;side++){TextView sample=new TextView(this);sample.setText(side==0?tr("Me 9"):tr("Opponent 10"));sample.setTextSize(14);sample.setGravity(Gravity.CENTER);sample.setTextColor(side==0?p.leftInk:p.rightInk);sample.setBackgroundColor(side==0?p.left:p.right);sample.setPadding(dp(4),dp(10),dp(4),dp(10));row.addView(sample,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}row.setContentDescription(tr(p.name)+tr(" preview"));c.addView(row);paragraph(c,tr(p.help));
        }
        paragraph(c,tr("Scoreboard colours apply to every sport. Left stays you or your team. Serving keeps its own badge and outline. Ambient stays dim and monochrome. Changes save immediately."));backButton(c,"Back to Settings",this::showSettings);
    }
    private void showBrightness(){LinearLayout c=panel("Watch brightness");paragraph(c,tr("Keep Score follows your watch’s system brightness. Enable Auto brightness or Adaptive brightness in the watch’s Display settings if available. Sunlight colours increase score contrast; the system controls the screen’s brightness."));button(c,tr("Open watch display settings"),()->{try{startActivity(new Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS));}catch(android.content.ActivityNotFoundException unavailable){message(tr("Open watch Settings"),tr("On the watch, open Settings → Display → Brightness. Names may vary by watch."),this::showBrightness);}});backButton(c,"Back to Settings",this::showSettings);}
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
        if(!saved){LinearLayout warning=panel("Save failed");paragraph(warning,tr("The watch did not confirm saving this change. Keep the app open and free storage before retrying."));button(warning,tr("Retry save"),()->{if(persistScore())closePanel();});}
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
            button(content, tr("Undo retirement"), ()->{
                if (!before.equals(book.encode())) { message(tr("Match changed"), tr("Review the match before undoing."), this::showMatchControls); return; }
                if (book.undo() && afterMatchChange()) closePanel();
            });
            backButton(content, tr("Cancel"), cancel);
        } else if(book.current().undoEnteredSet()){
            final String before=book.encode();LinearLayout content=panel("Undo entered set?");paragraph(content,tr("This set was entered as a final score. Its point order was not recorded, so Undo removes the whole entered set. Other sets are kept."));
            button(content,tr("Undo entered set"),()->{if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the match before undoing."),this::showMatchControls);return;}if(book.undo()&&afterMatchChange())closePanel();});backButton(content,tr("Cancel"),cancel);
        }else if(book.undo()&&afterMatchChange())closePanel();
    }
    private LinearLayout manualPanel(String title){LinearLayout content=panel(title);panel.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);return content;}
    private void showAddPastMatch(){choosePastFormat();}
    private void chooseSetWinner(String title,String opponent,int oldMe,int oldOther,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        MatchFormat f=manualMatch==null?book.current().format():manualMatch.format;int index=manualMatch==null?(book.current().nextReady()?book.current().gameCount():book.current().gameCount()-1):manualEditingIndex>=0?manualEditingIndex:manualMatch.size();
        boolean tie=manualMatch==null?book.current().matchTie(index):manualMatch.matchTie(index);
        if(!f.legacy()){chooseGenericScore(title,opponent,f,tie,oldMe,oldOther,apply,back);return;}
        LinearLayout content=manualPanel(title);paragraph(content,tr("Who won this set?"));
        if(oldMe+oldOther>0)ManualEntryUi.score(content,tr("Score to enter"),opponent,oldMe,oldOther,null);
        int loser=oldMe+oldOther==0?7:Math.min(oldMe,oldOther);
        button(content,tr("Me won"),()->showSetScoreWheel(title,opponent,0,loser,apply,back));
        styledButton(content,tr("Opponent won"),()->showSetScoreWheel(title,opponent,1,loser,apply,back),ButtonKind.SCORE_OPPONENT);
        backButton(content,tr("Cancel"),back);
    }
    private void showSetScoreWheel(String title,String opponent,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);paragraph(content,(winner==0?tr("Me"):tr("Opponent"))+tr(" won · choose the losing score"));
        ManualEntryUi.ScoreCard preview=ManualEntryUi.score(content,tr("Final set score"),opponent,0,0,null);
        NumberPicker wheel=ManualEntryUi.wheel(content,tr("Losing score"),0,(ScoreState.MAX_RALLIES-2)/2,initial);
        LinearLayout controls=ManualEntryUi.row(content);Button minus=button(controls,"−",()->{}),plus=button(controls,"+",()->{});ManualEntryUi.equal(minus,plus);
        Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);preview.scores(winner==0?high:low,winner==0?low:high);minus.setEnabled(low>0);plus.setEnabled(low<wheel.getMaxValue());};
        minus.setContentDescription(tr("Decrease losing score"));plus.setContentDescription(tr("Increase losing score"));minus.setOnClickListener(v->{wheel.setValue(Math.max(0,wheel.getValue()-1));refresh.run();});plus.setOnClickListener(v->{wheel.setValue(Math.min(wheel.getMaxValue(),wheel.getValue()+1));refresh.run();});wheel.setOnValueChangedListener((p,a,b)->refresh.run());refresh.run();
        button(content,manualMatch!=null?tr("Use set score"):tr("Review set score"),()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);apply.accept(winner==0?high:low,winner==0?low:high);});
        paragraph(content,tr("Quick scores · Me–Opponent"));
        for(int[] presets:new int[][]{{0,5},{7,9}}){LinearLayout row=ManualEntryUi.row(content);Button[] buttons=new Button[2];for(int i=0;i<2;i++){final int low=presets[i],high=ManualMatchEntry.winningScore(low);buttons[i]=button(row,(winner==0?high:low)+"–"+(winner==0?low:high),()->{wheel.setValue(low);refresh.run();});}ManualEntryUi.equal(buttons[0],buttons[1]);}
        button(content,tr("Change winner"),()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);chooseSetWinner(title,opponent,winner==0?high:low,winner==0?low:high,apply,back);});
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
            button(content,tr("Save set score"),()->{
                if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the match before saving."),this::showMatchControls);return;}
                if(book.enterCompletedSet(a,b)&&afterMatchChange()){
                    if(book.current().complete()){if(book.result(book.current().id())!=null)showResult(book.current().id(),0);else if(!book.currentDeleted())showSavePending();else showMatchControls();}
                    else showEnteredSetSaved(number,a,b);
                }
            });backButton(content,tr("Cancel"),this::showMatchControls);
        },this::showMatchControls);
    }
    private void showEnteredSetSaved(int number,int me,int other){
        LinearLayout content=manualPanel(tr("Set saved"));ManualEntryUi.score(content,tr("Set ")+number,opponentName(book.current().id()),me,other,null);
        button(content,tr("Score next set live"),()->{if(book.current().complete()){showMatchControls();return;}if(book.current().nextReady())book.nextGame();setSession(true,false);if(afterMatchChange())closePanel();});
        navigationButton(content,tr("Enter next set score"),this::enterCurrentSet);backButton(content,"Back to Current match",this::showMatchControls);
    }
    private String manualOpponent(ManualMatchEntry draft){OpponentDirectory.Entry e=opponents.get(draft.opponentId());return e==null?tr("Opponent"):e.name;}
    private void showManualMatch(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}
        LinearLayout content=manualPanel(tr("Add past match"));paragraph(content,tr("Add a separate completed match to history."));
        if(draft.format.legacy()){LinearLayout formats=ManualEntryUi.row(content);Button bo3=button(formats,tr("Best of 3"),()->{draft.chooseFormat(3);showManualMatch();}),bo5=button(formats,tr("Best of 5"),()->{draft.chooseFormat(5);showManualMatch();});ManualEntryUi.equal(bo3,bo5);bo3.setEnabled(draft.bestOf()!=3&&draft.canChooseFormat(3));bo5.setEnabled(draft.bestOf()!=5&&draft.canChooseFormat(5));}else for(int option:draft.format.bestOptions()){Button length=button(content,tr("Best of ")+option,()->{draft.chooseFormat(option);showManualMatch();});length.setEnabled(option!=draft.bestOf()&&draft.canChooseFormat(option));}
        paragraph(content,tr(draft.format.name())+" · "+ui.rules(draft.format)+tr(" · Best of ")+draft.bestOf());
        personButton(content,tr("Opponent: ")+(draft.opponentId().isEmpty()?tr("No name"):manualOpponent(draft))+tr(" (optional)"),()->showManualOpponent(0));
        if(draft.format.doubles){personButton(content,tr("Partner: ")+playerName(draft.partnerId),()->pickPlayer(tr("Partner"),id->{draft.partnerId=id;showManualMatch();},this::showManualMatch,0));personButton(content,tr("Opponent 2: ")+playerName(draft.otherId),()->pickPlayer(tr("Opponent 2"),id->{draft.otherId=id;showManualMatch();},this::showManualMatch,0));}
        button(content,tr("Date: ")+ui.date(draft.date()),()->{manualDate=draft.date();showManualDate();});
        for(int i=0;i<draft.size();i++){final int index=i;ManualEntryUi.score(content,tr("Set ")+(i+1)+tr(" · tap to edit"),manualOpponent(draft),draft.points(i,0),draft.points(i,1),()->editManualSet(draft,index));}
        if(!draft.complete())button(content,tr("Add set ")+(draft.size()+1),()->editManualSet(draft,draft.size()));
        else paragraph(content,tr("Match complete. Check the set scores, opponent and date before saving."));
        if(draft.size()>0)deleteButton(content,tr("Remove last set"),()->{draft.removeLast();showManualMatch();});
        paragraph(content,tr("Match times are left as Not recorded. You are entering final set scores, not a rally log."));
        Button save=button(content,tr("Save match result"),()->saveManualMatch(draft));save.setEnabled(draft.complete());
        backButton(content,tr("Cancel"),()->{manualMatch=null;showResults(resultsPage);});
    }
    private void editManualSet(ManualMatchEntry draft,int index){
        manualEditingIndex=index;int me=index<draft.size()?draft.points(index,0):0,other=index<draft.size()?draft.points(index,1):0;
        chooseSetWinner(tr("Set ")+(index+1)+tr(" score"),manualOpponent(draft),me,other,(a,b)->{if(manualMatch!=draft)return;try{draft.setScore(index,a,b);showManualMatch();}catch(Exception invalid){message(tr("Check set order"),safe(invalid),this::showManualMatch);}},this::showManualMatch);
    }
    private void showManualOpponent(int requestedPage){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));LinearLayout content=manualPanel(tr("Choose opponent"));
        personButton(content,tr("No name"),()->{draft.opponentId("");showManualMatch();});
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry e=entries.get(i);personButton(content,e.name,()->{draft.opponentId(e.id);showManualMatch();});}
        if(page>0)navigationButton(content,tr("Previous names"),()->showManualOpponent(page-1));if((page+1)*10<entries.size())navigationButton(content,tr("More names"),()->showManualOpponent(page+1));paragraph(content,tr("Names are optional. Add them from Opponents → Edit names on phone."));backButton(content,tr("Cancel"),this::showManualMatch);
    }
    private void showManualDate(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}LinearLayout content=manualPanel(tr("Match date"));paragraph(content,ui.date(manualDate));
        button(content,tr("Today"),()->{draft.date(LocalDate.now(),LocalDate.now());showManualMatch();});button(content,tr("Yesterday"),()->{draft.date(LocalDate.now().minusDays(1),LocalDate.now());showManualMatch();});
        button(content,tr("Day: ")+manualDate.getDayOfMonth(),()->manualDateWheel(tr("Day"),1,manualDate.lengthOfMonth(),manualDate.getDayOfMonth(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),manualDate.getMonthValue(),v)));
        button(content,tr("Month: ")+manualDate.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,ui.locale),()->manualDateWheel(tr("Month"),1,12,manualDate.getMonthValue(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),v,manualDate.getDayOfMonth())));
        button(content,tr("Year: ")+manualDate.getYear(),()->manualDateWheel(tr("Year"),1900,LocalDate.now().getYear(),manualDate.getYear(),v->manualDate=ManualMatchEntry.changeDate(manualDate,v,manualDate.getMonthValue(),manualDate.getDayOfMonth())));
        button(content,tr("Use date"),()->{try{draft.date(manualDate,LocalDate.now());showManualMatch();}catch(Exception invalid){message(tr("Check date"),safe(invalid),this::showManualDate);}});backButton(content,tr("Cancel"),this::showManualMatch);
    }
    private void manualDateWheel(String title,int min,int max,int value,java.util.function.IntConsumer apply){
        LinearLayout content=manualPanel(title);NumberPicker wheel=ManualEntryUi.wheel(content,title,min,max,value);if(title.equals(tr("Month"))){String[] months=new String[12];for(int i=1;i<=12;i++)months[i-1]=java.time.Month.of(i).getDisplayName(java.time.format.TextStyle.SHORT,ui.locale);wheel.setDisplayedValues(months);}
        button(content,ui.format("Use {0}","{0} übernehmen","Utiliser : {0}","Usar {0}",ui.code.equals("de")?title:title.toLowerCase(ui.locale)),()->{apply.accept(wheel.getValue());showManualDate();});backButton(content,"Cancel",this::showManualDate);
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
    private void showEndMatch(){
        if(book.current().complete()){showMatchControls();return;}
        LinearLayout content=panel("Retirement result");paragraph(content,tr("Who is quitting? The other player wins by retirement. Scores already played are kept; no extra sets or points are awarded."));
        button(content,book.current().format().doubles?tr("Our team retires"):tr("I quit"),()->confirmRetirement(0));button(content,book.current().format().doubles?tr("Opposing team retires"):tr("Opponent quit"),()->confirmRetirement(1));backButton(content,tr("Cancel"),this::showMatchOptions);
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
                if(live!=null)live.discard();book=next;opponents=names;manualMatch=null;scoreMenu=false;counter.match(book.current());updateDisplay();showMenu();
            }catch(Exception error){message(tr("Match not cancelled"),safe(error),this::showMatchControls);}
        });
        backButton(content,tr("Keep playing"),back);
    }
    private void confirmRetirement(int player){
        if(book.current().complete()){showMatchControls();return;}
        final String before=book.encode();LinearLayout content=panel("Save retirement?");
        paragraph(content,(player==0?tr("You quit. ")+opponentName(book.current().id())+tr(" wins."):opponentName(book.current().id())+tr(" quits. You win."))+"\n\n"+currentGamesText()+tr("\n\nSave this result as retired? You can undo the retirement from the saved result."));
        button(content,tr("Save retirement"),()->{
            if(!before.equals(book.encode())){message(tr("Match changed"),tr("Review the current match before ending it."),this::showEndMatch);return;}
            if(book.retire(player)&&afterMatchChange()){
                if(book.result(book.current().id())!=null)showResult(book.current().id(),0);else if(!book.currentDeleted())showSavePending();else showMatchControls();
            }
        });backButton(content,tr("Cancel"),this::showEndMatch);
    }
    private void quickRematch(){
        MatchState m=book.current();if(!m.complete()){showMatchControls();return;}
        selectSport(m.format().sport);newMatchFormat=m.format();newMatchBestOf=m.bestOf();newMatchOpponent=availablePlayer(opponents.roleId(m.id(),0));newMatchPartner=availablePlayer(opponents.roleId(m.id(),1));newMatchOther=availablePlayer(opponents.roleId(m.id(),2));newFirstServer=m.firstServer(0);newFirstReceiver=m.firstReceiver(0);newMatchReturn=this::showMatchControls;startMatch(newMatchBestOf,newMatchOpponent);
    }
    private String availablePlayer(String id){OpponentDirectory.Entry e=opponents.get(id);return e==null||e.archived?"":id;}
    private MatchPresets presets(){return MatchPresets.decode(preferences.getString("favourite_formats",""));}
    private void showPresets(boolean setup){
        LinearLayout c=panel("Favourite formats");java.util.List<MatchPresets.Entry> list=presets().list(selectedSport);if(list.isEmpty())paragraph(c,tr("Save a favourite from New match to reuse its scoring rules and match length."));
        for(MatchPresets.Entry e:list)navigationButton(c,e.name+"\n"+ui.rules(e.format)+tr(" · BO")+e.best,()->{if(!allowSport(e.format.sport))return;if(!setup)showNewMatch(this::showMenu);newMatchFormat=e.format;newMatchBestOf=e.best;newFirstServer=e.format.pickleball()?0:-1;newFirstReceiver=e.format.pickleball()?1:-1;if(!e.format.doubles){newMatchPartner=newMatchOther="";}showNewMatchOptions();});
        if(!list.isEmpty())navigationButton(c,tr("Remove a favourite"),()->removePreset(setup));backButton(c,setup?"Back to setup":"Back to Menu",setup?this::showNewMatchOptions:this::showMenu);
    }
    private void removePreset(boolean setup){LinearLayout c=panel("Remove favourite");for(MatchPresets.Entry e:presets().list(selectedSport))deleteButton(c,e.name,()->{MatchPresets p=presets();p.remove(e.id);if(preferences.edit().putString("favourite_formats",p.encode()).commit())showPresets(setup);else message(tr("Save failed"),tr("Favourite was kept."),()->showPresets(setup));});backButton(c,tr("Cancel"),()->showPresets(setup));}
    private void savePreset(){
        LinearLayout c=panel("Save favourite");EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setText(tr(newMatchFormat.name())+" "+tr(newMatchFormat.mode())+" BO"+newMatchBestOf);input.setContentDescription(tr("Favourite name"));c.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        button(c,tr("Save favourite"),()->{try{MatchPresets p=presets();p.add(input.getText().toString(),newMatchFormat,newMatchBestOf);if(!preferences.edit().putString("favourite_formats",p.encode()).commit())throw new IllegalStateException(tr("Favourite was not saved"));showNewMatchOptions();}catch(Exception e){input.setError(safe(e));}});backButton(c,tr("Cancel"),this::showNewMatchOptions);
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
        LinearLayout content=panel("New match","new-match-setup");section(content,tr("Scoring & format"));
        formatControls(content,newMatchFormat,f->{newMatchFormat=f;newFirstServer=f.pickleball()?0:-1;newFirstReceiver=f.pickleball()?1:-1;if(!f.doubles){newMatchPartner=newMatchOther="";}showNewMatchOptions();},this::showNewMatchOptions);
        navigationButton(content,tr("Match length: Best of ")+newMatchBestOf,()->chooseLength(newMatchFormat,newMatchBestOf,value->{newMatchBestOf=value;showNewMatchOptions();},this::showNewMatchOptions));
        paragraph(content,ui.rules(newMatchFormat));
        navigationButton(content,tr("Use favourite format"),()->showPresets(true));
        navigationButton(content,tr("Save as favourite"),this::savePreset);
        section(content,tr("Players · optional"));
        personButton(content,tr("Opponent: ")+(newMatchOpponent.isEmpty()?tr("No name"):chosen.name)+tr(" (optional)"),()->showOpponentPicker(null,false,0));
        if(newMatchFormat.doubles){personButton(content,tr("Partner: ")+playerName(newMatchPartner),()->pickPlayer(tr("Partner"),id->{newMatchPartner=id;showNewMatchOptions();},this::showNewMatchOptions,0));personButton(content,tr("Opponent 2: ")+playerName(newMatchOther),()->pickPlayer(tr("Opponent 2"),id->{newMatchOther=id;showNewMatchOptions();},this::showNewMatchOptions,0));}
        section(content,newMatchFormat.pickleball()?tr("Starting servers"):tr("Serving · optional"));
        navigationButton(content,tr("First server: ")+slotName(newFirstServer,null),()->chooseServer(newMatchFormat,newFirstServer,slot->{newFirstServer=slot;newFirstReceiver=newMatchFormat.pickleball()?1-slot%2:-1;showNewMatchOptions();},this::showNewMatchOptions));
        if(newMatchFormat.doubles&&newFirstServer>=0)navigationButton(content,(newMatchFormat.nested()||newMatchFormat.pickleball()?tr("Next opposing server: "):tr("First receiver: "))+slotName(newFirstReceiver,null),()->chooseReceiver(newMatchFormat,newFirstServer,slot->{newFirstReceiver=slot;showNewMatchOptions();},this::showNewMatchOptions));
        button(content,newMatchCreateLabel==null?tr("Create match"):newMatchCreateLabel,()->confirmNewMatch(newMatchBestOf));
        paragraph(content,tr("Tap the first point to start play and timing."));backButton(content,tr("Cancel"),newMatchReturn);
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
        MatchState match=book.current();LinearLayout content=panel("Match details");
        showSetScores(content,match,match.format().doubles?teamName(match.id(),0):ownName(),match.format().doubles?teamName(match.id(),1):opponentName(match.id()));
        paragraph(content,tr("Started")+"\n"+ui.scoreTerms(book.currentStartedText()));
        if(match.retired())paragraph(content,match.retiredPlayer()==0?tr("Me retired · Opponent won"):tr("Opponent retired · You won"));
        if(match.nextReady())button(content,tr("Next set"),()->{book.nextGame();if(afterMatchChange())closePanel();});
        MatchResult result=book.result(match.id());if(result!=null)navigationButton(content,tr("View result"),()->showResult(result.id,resultsPage,this::showCurrentMatch,"Back to Match details"));
        if(match.complete()&&result==null&&!book.currentDeleted())button(content,tr("Save match result"),this::saveCurrentResult);
        if(match.complete()&&book.currentDeleted())paragraph(content,tr("This result was deleted."));
        backButton(content,"Back to Current match",this::showMatchControls);
    }
    void showSharedMatchDetails(){
        MatchState match=rooms.displayed();
        if(match==null){rooms.hub();return;}
        LinearLayout page=roomPanel(tr("Match details"),"room-details");
        showSetScores(page,match,rooms.team(0),rooms.team(1));
        backButton(page,rooms.backToSharing(),rooms::hub);
    }
    boolean roomDetailsVisible(){return panel!=null&&"room-details".equals(panelScrollKey);}
    private void showSetScores(LinearLayout page,MatchState match,String left,String right){
        boolean sets=match.format().nested();
        String unit=ui.pick(sets?"Set":"Game","Satz",sets?"Set":"Manche",sets?"Set":"Juego");
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
        if(book.saveCompleted(LocalDate.now())){if(afterMatchChange())showResult(match.id(),0);}
        else showSavePending();
    }
    private void showSavePending(){
        LinearLayout content=panel("Result not saved yet");paragraph(content,tr("Result storage is full. Your completed match is kept on the scoreboard. Delete old results, then open Current match and Save match result."));
        navigationButton(content,tr("Results"),()->openResults(this::showMenu,"Back to Menu"));backButton(content,"Back to Current match",this::showMatchControls);
    }
    private void openResults(Runnable back,String label){resultsReturn=back;resultsReturnLabel=label;showResults(0);}
    private void showResults(int requestedPage){
        List<MatchResult> items=filteredResults();int page=Math.max(0,Math.min(requestedPage,Math.max(0,(items.size()-1)/10)));
        resultsPage=page;LinearLayout content=panel("Results","results:"+selectedSport+":"+filterMode+":"+page+":"+filterOpponent+":"+filterFrom+":"+filterTo);
        LinearLayout tools=ManualEntryUi.row(content);Button filters=navigationButton(tools,tr("Filters"),this::showResultFilters),options=navigationButton(tools,tr("Options"),this::showResultOptions),add=navigationButton(tools,tr("Add past match"),this::showAddPastMatch);filters.setContentDescription(tr("Filter results"));options.setContentDescription(tr("Result options"));
        for(Button tool:new Button[]{filters,options,add}){tool.setCompoundDrawables(null,null,null,null);tool.setGravity(Gravity.CENTER);tool.setTextSize(11);tool.setPadding(dp(2),dp(4),dp(2),dp(4));tool.setMinWidth(0);tool.setMinimumWidth(0);LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);slot.bottomMargin=dp(4);tool.setLayoutParams(slot);}
        if(resultFiltersActive())navigationButton(content,tr("Reset all filters"),this::resetResultFilters);
        if(items.isEmpty())paragraph(content,book.count()==0?tr("No saved matches yet. Finished matches are saved here automatically."):tr("No matches fit these filters."));
        else{
            paragraph(content,items.size()+tr(" of ")+book.count()+tr(" saved\nPage ")+(page+1)+tr(" of ")+Math.max(1,(items.size()+9)/10));
            for(int i=page*10;i<Math.min(items.size(),page*10+10);i++){
                MatchResult result=items.get(i);resultCard(content,result,()->showResult(result.id,page));
            }
            if(page>0)navigationButton(content,tr("Newer results"),()->showResults(page-1));
            if((page+1)*10<items.size())navigationButton(content,tr("Older results"),()->showResults(page+1));
        }
        backButton(content,resultsReturnLabel,resultsReturn);
    }
    private void showResultOptions(){
        LinearLayout content=panel("Result options");
        navigationButton(content,tr("View results on phone"),()->openPhone("results",false,()->showResults(resultsPage),"Back to Results"));
        navigationButton(content,tr("Export CSV"),this::showCsvExport);
        navigationButton(content,tr("Import CSV"),()->openPhone("import",false,this::showResultOptions,"Back to Result options"));
        if(!sportResults(-1).isEmpty())deleteButton(content,tr("Delete all results"),()->confirmDeleteAll(resultsPage));
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
        LinearLayout content=panel("Statistics");
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
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(16));shape.setColor(0xff10283f);shape.setStroke(dp(1),0xff315b7d);card.setBackground(shape);
        TextView heading=paragraph(card,title);heading.setTextSize(16);heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);heading.setTextColor(0xffa6d7ff);
        TextView values=paragraph(card,text);values.setTextSize(14);values.setTextColor(0xffedf4ff);
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);position.bottomMargin=dp(10);content.addView(card,position);
    }
    private void showResult(String id,int page){
        showResult(id,page,()->showResults(page),"Back to Results");
    }
    private void showResult(String id,int page,Runnable back,String backLabel){
        MatchResult result=book.result(id);if(result==null){showResults(page);return;}
        Runnable again=()->showResult(id,page,back,backLabel);
        LinearLayout content=panel(result.heading());
        paragraph(content,tr(result.format.name())+" · "+ui.rules(result.format));if(result.format.doubles)personButton(content,tr("Players"),()->showTeam(id,result.format,again));else personButton(content,tr("Opponent: ")+opponentName(id),()->showOpponentPicker(id,false,0,again));
        resultCard(content,result,null);paragraph(content,tr("Set scores: ")+ownName()+"–"+opponentName(result.id));
        navigationButton(content,tr("Share result card"),()->openPhone("results",false,again,"Back to Result",id));
        if(id.equals(book.current().id()))button(content,result.retired()?tr("Undo retirement"):book.current().undoEnteredSet()?tr("Undo entered set"):tr("Undo last point"),()->undoPoint(again));
        if(id.equals(book.current().id())){button(content,tr("Quick rematch"),this::quickRematch);button(content,tr("New match"),()->showNewMatch(again));}
        deleteButton(content,tr("Delete this result"),()->confirmDelete(id,page,again));
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
    void showSports(){
        if(rooms!=null && rooms.readOnly()){rooms.hub();return;}
        sharedArea=true;scoreMenu=false;
        LinearLayout content=panel("Choose sport");
        if(hasCurrentMatch()&&!book.current().complete())button(content,(needsResume()?tr("Resume "):tr("Current match")+" · ")+tr(book.current().format().name()),this::showMatchControls);
        if(sportLocked())paragraph(content,ui.format("Finish or cancel your {0} match to change sport. Paused matches stay locked.","Beende deine Partie ({0}) oder brich sie ab, um die Sportart zu wechseln. Bei einer Pause bleibt sie gesperrt.","Termine ou annule ton match de {0} pour changer de sport. Une pause conserve le sport actuel.","Termina o cancela tu partido de {0} para cambiar de deporte. Una pausa mantiene el deporte actual.",tr(book.current().format().name())));
        for(String sport:ui.sortedSports()){Button choice=navigationButton(content,tr(MatchFormat.name(sport)),()->{if(selectSport(sport)){sportMenuFromSelector=true;showMenu();}});sportButton(choice,sport);boolean enabled=!sportLocked()||sport.equals(book.current().format().sport);choice.setEnabled(enabled);choice.setAlpha(enabled?1f:.4f);choice.setContentDescription(tr(MatchFormat.name(sport))+(enabled?tr(", choose sport"):tr(", locked until the current match is finished or cancelled")));}
        section(content,"Keep Score");
        sharingButton(content,MatchRooms.Entry.SPORTS);
        personButton(content,tr("Players"),()->openPlayers(this::showSports,"Back to sports"));settingsButton(content,tr("Settings"),()->openSettings(this::showSports,"Back to sports"));
        backButton(content,tr("Close app"),this::finish);
    }
    private boolean selectSport(String sport){if(!allowSport(sport))return false;if(!selectedSport.equals(sport)){filterOpponent=null;filterFrom=filterTo=null;filterMode=-1;resultsPage=0;}selectedSport=sport;preferences.edit().putString("selected_sport",sport).apply();return true;}
    private void showSportRules(){
        LinearLayout content=panel(tr(MatchFormat.name(selectedSport))+tr(" rules"));
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
    private void chooseServer(MatchFormat f,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel("First server");paragraph(content,f.nested()&&f.doubles?tr("Choose the first server for this set. Set the next opposing server in the following option."):tr("Choose the initial server for this game. The indicator follows recorded points; Undo restores it."));
        if(!f.pickleball())navigationButton(content,tr("Not set"),()->apply.accept(-1));for(int slot=0;slot<(f.doubles?4:2);slot++){final int choice=slot;personButton(content,(choice==selected?"✓ ":"")+(choice==0?ownName():choice==1?tr("Opponent 1"):choice==2?tr("Partner"):tr("Opponent 2")),()->apply.accept(choice));}backButton(content,tr("Cancel"),back);
    }
    private void chooseReceiver(MatchFormat f,int server,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel(f.nested()||f.pickleball()?tr("Next opposing server"):tr("First receiver"));
        for(int slot=0;slot<4;slot++)if(server<0||slot%2!=server%2){final int choice=slot;personButton(content,choice==0?tr("Me"):choice==1?tr("Opponent 1"):choice==2?tr("Partner"):tr("Opponent 2"),()->apply.accept(choice));}backButton(content,tr("Cancel"),back);
    }
    private void showServing(){
        MatchState match=book.current();int index=match.gameCount()-1;LinearLayout content=panel("Serving indicator","serving:"+match.id());
        paragraph(content,tr("Next serve: ")+slotName(match.server(),match.id())+(match.format().pickleball()?tr("\nServe call: ")+match.game().pickleCall():"")+tr("\nInitial choices apply to ")+tr(match.format().unit()).toLowerCase()+" "+(index+1)+".");
        if(match.format().pickleball()&&match.game().rallies()>0){paragraph(content,tr("Starting servers are fixed once rallies are recorded. Undo to zero to change them."));backButton(content,"Back to Match options",this::showMatchOptions);return;}
        navigationButton(content,tr("First server: ")+slotName(match.firstServer(index),match.id()),()->chooseServer(match.format(),match.firstServer(index),slot->{match.serving(slot,match.format().pickleball()?1-slot%2:-1);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        if(match.format().doubles&&match.firstServer(index)>=0)navigationButton(content,(match.format().nested()||match.format().pickleball()?tr("Next opposing server: "):tr("First receiver: "))+slotName(match.firstReceiver(index),match.id()),()->chooseReceiver(match.format(),match.firstServer(index),slot->{match.serving(match.firstServer(index),slot);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        backButton(content,"Back to Match options",this::showMatchOptions);
    }
    private void pickPlayer(String title,java.util.function.Consumer<String> choose,Runnable back,int requestedPage){
        List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));LinearLayout content=panel(title);
        personButton(content,tr("No name"),()->choose.accept(""));for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry entry=entries.get(i);playerButton(content,entry,()->choose.accept(entry.id));}
        if(page>0)navigationButton(content,tr("Previous names"),()->pickPlayer(title,choose,back,page-1));if((page+1)*10<entries.size())navigationButton(content,tr("More names"),()->pickPlayer(title,choose,back,page+1));
        personButton(content,tr("Add player"),()->showNameEditor(null,null,back,choose));backButton(content,tr("Cancel"),back);
    }
    private void showTeam(String id,MatchFormat format,Runnable back){
        LinearLayout content=panel("Players");paragraph(content,tr("Our team: ")+teamName(id,0)+tr("\nOpposing team: ")+teamName(id,1));
        for(int slot=0;slot<3;slot++){final int role=slot;personButton(content,(slot==0?tr("Opponent 1"):slot==1?tr("Partner"):tr("Opponent 2"))+": "+playerName(opponents.roleId(id,slot)),()->pickPlayer(tr("Choose player"),chosen->{String before=opponents.encode();try{String[] team={opponents.roleId(id,0),opponents.roleId(id,1),opponents.roleId(id,2)};team[role]=chosen;opponents.assignTeam(id,team[0],team[1],team[2]);if(!persistScore())throw new IllegalStateException(tr("Save failed"));updateDisplay();showTeam(id,format,back);}catch(Exception error){opponents=OpponentDirectory.decode(before);message(tr("Player not saved"),safe(error),()->showTeam(id,format,back));}},()->showTeam(id,format,back),0));}
        backButton(content,tr("Back"),back);
    }
    private String sportsText(String id){java.util.Set<String> sports=opponents.sportsFor(id,book.newestFirst());java.util.List<String> names=new java.util.ArrayList<>();for(String sport:ui.sortedSports())if(sports.contains(sport))names.add(tr(MatchFormat.name(sport)));return names.isEmpty()?tr("No saved results yet"):tr("Results: ")+android.text.TextUtils.join(", ",names);}
    private Button playerButton(LinearLayout content,OpponentDirectory.Entry entry,Runnable action){
        java.util.Set<String> sports=opponents.sportsFor(entry.id,book.newestFirst());StringBuilder label=new StringBuilder(entry.name);if(!sports.isEmpty()){label.append('\n');for(String sport:ui.sortedSports())if(sports.contains(sport))label.append("◦ ");}
        android.text.SpannableString text=new android.text.SpannableString(label);int start=entry.name.length()+1;for(String sport:ui.sortedSports())if(sports.contains(sport)){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));text.setSpan(new android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BASELINE),start,start+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);start+=2;}
        Button button=personButton(content,entry.name,action);button.setText(text);button.setContentDescription(entry.name+". "+sportsText(entry.id));return button;
    }
    private void choosePastFormat(){
        if(selectedSport.equals("squash")){manualMatch=new ManualMatchEntry(LocalDate.now());showManualMatch();return;}
        MatchFormat f;try{f=MatchFormat.decode(preferences.getString("last_format_"+selectedSport,MatchFormat.defaults(selectedSport).encode()));}catch(Exception error){f=MatchFormat.defaults(selectedSport);}showPastFormat(f);
    }
    private void showPastFormat(MatchFormat f){LinearLayout content=panel("Past match rules");paragraph(content,tr(f.name())+" · "+ui.rules(f));formatControls(content,f,this::showPastFormat,()->showPastFormat(f));button(content,tr("Continue"),()->{manualMatch=new ManualMatchEntry(LocalDate.now(),f);showManualMatch();});backButton(content,tr("Cancel"),()->showResults(resultsPage));}
    private void chooseGenericScore(String title,String opponent,MatchFormat f,boolean tie,int me,int other,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);paragraph(content,tr(f.name())+" · "+(tie?tr("Match tie-break to 10"):ui.rules(f))+tr("\nChoose the winner, then the losing score."));
        button(content,tr("Me / our team won"),()->genericScoreWheel(title,opponent,f,tie,0,Math.min(me,other),apply,back));styledButton(content,tr("Opponent / other team won"),()->genericScoreWheel(title,opponent,f,tie,1,Math.min(me,other),apply,back),ButtonKind.SCORE_OPPONENT);backButton(content,tr("Cancel"),back);
    }
    private void genericScoreWheel(String title,String opponent,MatchFormat f,boolean tie,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);int max=f.nested()&&!tie?6:Math.min(4999,tie?4999:f.cap()-1);NumberPicker wheel=ManualEntryUi.wheel(content,tr("Losing score"),0,max,Math.min(max,initial));TextView preview=paragraph(content,"");Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);preview.setText((winner==0?tr("Me / our team "):tr("Opponent / other team "))+high+"–"+low);};wheel.setOnValueChangedListener((picker,a,b)->refresh.run());refresh.run();
        button(content,tr("Use score"),()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);apply.accept(winner==0?high:low,winner==0?low:high);});backButton(content,tr("Cancel"),back);
    }
    private String updateDescription(){
        String text=tr("Installed: v")+BuildConfig.VERSION_NAME+"\n\n"+ui.status(updateStatus);
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
    private void showOpponents(int requestedPage){
        List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));
        LinearLayout content=panel("Players","opponents:"+page);
        personButton(content,tr("Your name: ")+ownName(),()->editOwnName(()->showOpponents(page)));
        if(entries.isEmpty())paragraph(content,tr("No names saved. Names are optional; you can keep playing without one."));
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry entry=entries.get(i);playerButton(content,entry,()->showOpponent(entry.id,page));}
        if(page>0)button(content,tr("Previous names"),()->showOpponents(page-1));if((page+1)*10<entries.size())button(content,tr("More names"),()->showOpponents(page+1));
        button(content,tr("Add player"),()->showNameEditor(null,null,()->showOpponents(page)));
        navigationButton(content,tr("Edit names on phone"),()->openPhone("opponents",false,()->showOpponents(page),"Back to Players"));
        TextView legend=paragraph(content,tr("Icons show sports in saved results."));legend.setTextSize(11);
        backButton(content,playersReturnLabel,playersReturn);
    }
    private void showOpponent(String id,int page){
        OpponentDirectory.Entry entry=opponents.get(id);if(entry==null){showOpponents(page);return;}LinearLayout content=panel(entry.name,null,true);paragraph(content,sportsText(entry.id));
        button(content,tr("Rename"),()->showNameEditor(id,null,()->showOpponent(id,page)));
        deleteButton(content,tr("Remove from player list"),()->{LinearLayout confirm=panel("Remove opponent?");paragraph(confirm,tr("Remove ")+entry.name+tr(" from future selection? Past match results keep this name and remain filterable."));deleteButton(confirm,tr("Remove name"),()->{String before=opponents.encode();opponents.archive(id);if(persistScore())showOpponents(page);else{opponents=OpponentDirectory.decode(before);message(tr("Save failed"),tr("The name was not removed."),()->showOpponent(id,page));}});backButton(confirm,tr("Cancel"),()->showOpponent(id,page));});
        backButton(content,"Back to Players",()->showOpponents(page));
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
        LinearLayout content=panel("Your name");paragraph(content,tr("Shown on your score card and shared scoreboards. Leave blank to use Me."));
        nameInput=new EditText(this);nameInput.setSingleLine(true);nameInput.setTextColor(Color.WHITE);nameInput.setHint(tr("Your name"));nameInput.setContentDescription(tr("Your name"));nameInput.setText(preferences.getString("my_name",""));content.addView(nameInput,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));final EditText input=nameInput;
        button(content,tr("Save your name"),()->{try{String value=input.getText().toString().trim();if(!value.isEmpty())value=OpponentDirectory.validName(value);if(!preferences.edit().putString("my_name",value).commit())throw new IllegalStateException(tr("Name was not saved"));updateDisplay();back.run();}catch(Exception e){input.setError(safe(e));}});
        backButton(content,tr("Cancel"),back);
    }
    private void showNameEditor(String id,String matchId,Runnable back,java.util.function.Consumer<String> afterSave){
        OpponentDirectory.Entry entry=id==null?null:opponents.get(id);LinearLayout content=panel(entry==null?tr("Add player"):tr("Rename player"));
        nameInput=new EditText(this);nameInput.setSingleLine(true);nameInput.setTextColor(Color.WHITE);nameInput.setHintTextColor(0xffa7b6c9);nameInput.setHint(tr("Player name"));nameInput.setContentDescription(tr("Player name"));nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);if(entry!=null)nameInput.setText(entry.name);content.addView(nameInput,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));final EditText input=nameInput;
        button(content,tr("Speak name"),()->{try{Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,tr("Player name"));startActivityForResult(voice,81);}catch(Exception absent){input.setError(tr("Speech input is unavailable. Type a name or use Edit names on phone."));}});
        button(content,tr("Save name"),()->{String before=opponents.encode();try{String selected=id;if(selected==null)selected=opponents.add(input.getText().toString());else opponents.rename(selected,input.getText().toString());if(matchId!=null)opponents.assign(matchId,selected);if(!persistScore())throw new IllegalStateException(tr("The watch did not confirm saving"));if(afterSave!=null)afterSave.accept(selected);updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);input.setError(safe(error));}});
        paragraph(content,tr("You can also type names from Opponents → Edit names on phone."));backButton(content,tr("Cancel"),back);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK&&data!=null&&nameInput!=null){java.util.ArrayList<String> names=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(names!=null&&!names.isEmpty())nameInput.setText(names.get(0));}}
    private void showOpponentPicker(String matchId,boolean filtering,int requestedPage){
        showOpponentPicker(matchId,filtering,requestedPage,filtering?this::showResultFilters:matchId==null?this::showNewMatchOptions:this::showMatchOptions);
    }
    private void showOpponentPicker(String matchId,boolean filtering,int requestedPage,Runnable back){
        boolean starting=!filtering&&matchId==null;
        List<OpponentDirectory.Entry> entries=opponents.list(filtering);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));
        LinearLayout content=panel(filtering?tr("Filter by opponent"):tr("Choose opponent"));
        if(filtering)personButton(content,tr("All opponents"),()->{filterOpponent=null;resultsPage=0;showResultFilters();});
        personButton(content,filtering?tr("Unnamed"):tr("No name"),()->{if(filtering){filterOpponent="";resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent="";showNewMatchOptions();}else assignOpponent(matchId,"",back);});
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry e=entries.get(i);personButton(content,e.name+(e.archived?tr(" (removed)"):""),()->{if(filtering){filterOpponent=e.id;resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent=e.id;showNewMatchOptions();}else assignOpponent(matchId,e.id,back);});}
        if(page>0)navigationButton(content,tr("Previous names"),()->showOpponentPicker(matchId,filtering,page-1,back));if((page+1)*10<entries.size())navigationButton(content,tr("More names"),()->showOpponentPicker(matchId,filtering,page+1,back));
        if(starting)button(content,tr("Add player"),()->showNameEditor(null,null,this::showNewMatchOptions,id->newMatchOpponent=id));
        else if(!filtering){button(content,tr("Add player"),()->showNameEditor(null,matchId,back));navigationButton(content,tr("Edit names on phone"),()->openPhone("opponents",false,()->showOpponentPicker(matchId,false,page,back),"Back to Choose opponent"));}
        backButton(content,tr("Cancel"),back);
    }
    private void assignOpponent(String matchId,String id,Runnable back){String before=opponents.encode();try{opponents.assign(matchId,id);if(!persistScore())throw new IllegalStateException(tr("The watch did not confirm saving"));updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);message(tr("Name not saved"),safe(error),back);}}
    private void showResultFilters(){
        LinearLayout content=panel("Result filters");navigationButton(content,tr("Reset all filters"),this::resetResultFilters);if(!selectedSport.equals("squash")&&!selectedSport.equals("padel"))navigationButton(content,tr("Mode: ")+modeLabel(),()->chooseResultMode(this::showResultFilters));OpponentDirectory.Entry e=filterOpponent==null?null:opponents.get(filterOpponent);
        personButton(content,tr("Opponent: ")+(filterOpponent==null?tr("All"):filterOpponent.isEmpty()?tr("Unnamed"):e==null?tr("Unknown"):e.name),()->showOpponentPicker(null,true,0));
        paragraph(content,tr("Dates: ")+(filterFrom==null?tr("any"):ui.filterDate(filterFrom))+tr(" to ")+(filterTo==null?tr("any"):ui.filterDate(filterTo)));
        button(content,tr("Today"),()->{filterFrom=LocalDate.now();filterTo=filterFrom;showResults(0);});
        button(content,tr("Last 7 days"),()->{filterTo=LocalDate.now();filterFrom=filterTo.minusDays(6);showResults(0);});
        button(content,tr("This month"),()->{filterTo=LocalDate.now();filterFrom=filterTo.withDayOfMonth(1);showResults(0);});
        button(content,tr("Custom date range"),this::showDateRange);
        backButton(content,"Back to Results",()->showResults(resultsPage));
    }
    private boolean resultFiltersActive(){return filterOpponent!=null||filterFrom!=null||filterTo!=null||filterMode>=0;}
    private void resetResultFilters(){
        filterOpponent=null;filterFrom=filterTo=null;filterMode=-1;showResults(0);
        final ScrollView scroll=panelScroll;if(scroll!=null)scroll.post(()->scroll.scrollTo(0,0));
    }
    private void showDateRange(){
        LinearLayout content=panel("Date range");paragraph(content,tr("Use YYYY-MM-DD. Leave either date empty for no limit."));
        EditText from=new EditText(this),to=new EditText(this);for(EditText input:new EditText[]{from,to}){input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(0xffa7b6c9);input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);content.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));}
        from.setHint(tr("From: YYYY-MM-DD"));from.setContentDescription(tr("From date"));to.setHint(tr("To: YYYY-MM-DD"));to.setContentDescription(tr("To date"));if(filterFrom!=null)from.setText(filterFrom.toString());if(filterTo!=null)to.setText(filterTo.toString());
        button(content,tr("Apply dates"),()->{try{LocalDate a=from.getText().toString().trim().isEmpty()?null:LocalDate.parse(from.getText().toString().trim()),b=to.getText().toString().trim().isEmpty()?null:LocalDate.parse(to.getText().toString().trim());if(a!=null&&b!=null&&a.isAfter(b))throw new IllegalArgumentException(tr("From date must not follow To date"));filterFrom=a;filterTo=b;showResults(0);}catch(Exception invalid){from.setError(tr("Check both dates: YYYY-MM-DD, From before To"));}});backButton(content,tr("Cancel"),this::showResultFilters);
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
        }
    }
    void chooseSharingMatch(MatchRooms.Entry entry,boolean audience,String createLabel,Runnable ready){
        if(rooms.readOnly()){rooms.hub();return;}
        MatchState current=book.current();
        boolean usable=!current.complete()&&(hasCurrentMatch()||entry==MatchRooms.Entry.MATCH);
        boolean audienceFinal=audience&&current.complete()&&(entry==MatchRooms.Entry.MATCH||hasAudienceLink());
        if(usable||audienceFinal){ready.run();return;}
        if(entry!=MatchRooms.Entry.SPORTS){
            sharedArea=false;
            showNewMatch(rooms::hub,ready,createLabel);
            return;
        }
        LinearLayout page=roomPanel(tr("Choose sport"),"sharing-sport");
        for(String sport:ui.sortedSports()){
            Button choice=navigationButton(page,tr(MatchFormat.name(sport)),()->{
                if(!selectSport(sport))return;
                sharedArea=false;
                showNewMatch(()->chooseSharingMatch(entry,audience,createLabel,ready),ready,createLabel);
            });
            sportButton(choice,sport);
        }
        backButton(page,tr("Back"),rooms::hub);
    }
    boolean roomAlreadySaved(String id){return book.result(id)!=null;}
    boolean roomLocalFinished(){return book.current().complete();}
    boolean roomPageVisible(View page){return panel!=null && panel.isShowing() && page.getRootView()==panel.getWindow().getDecorView();}
    boolean roomHubVisible(){return panel!=null && "room-hub".equals(panelScrollKey);}
    boolean roomScoreVisible(){return panel==null;}
    void refreshRoomScore(){updateDisplay();}
    String roomSport(String sport){return tr(MatchFormat.name(sport));}
    void restoreLocalScore(){counter.match(book.current());counter.remote(null,false);updateDisplay();}
    LinearLayout roomPanel(String title,String key){sharedArea=true;LinearLayout content=panel(title,key);panelShortcut=false;return content;}
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
        LinearLayout c=panel("Live scoreboard","live");liveText=paragraph(c,tr(live.status()));LiveScoreClient.Session s=live.session();livePanelState=liveState(s);
        if(s!=null){if(s.ready){button(c,tr("Show audience QR"),()->showAudienceQr(s));paragraph(c,tr("Anyone with this link can view this match’s score, names and previous-meeting summary. Live links last up to eight hours; final results remain for ten minutes after completion. Audience members cannot change the score."));}else paragraph(c,tr("Creating your audience link… The QR will open when it is ready."));deleteButton(c,tr("Stop sharing"),()->{live.stop(true);showLive();});}
        else{button(c,tr("Start live sharing"),()->{try{live.start(liveSnapshot());LiveScoreClient.Session started=live.session();if(started!=null&&started.ready)showAudienceQr(started);else showLive();}catch(Exception e){message(tr("Cannot share"),safe(e),this::showLive);}});paragraph(c,tr("Share this match’s score, names and head-to-head summary with an audience. Previous meetings use the same sport and saved players; doubles requires both teams to match. Start with an internet connection, then share the QR link. Offline scores and final results retry when the connection returns."));}
        backButton(c,rooms.backToSharing(),rooms::hub);
    }
    private java.util.Map<String,String> backupSettings(){java.util.Map<String,String> out=new java.util.TreeMap<>();for(java.util.Map.Entry<String,?> e:preferences.getAll().entrySet())if(BackupArchive.settingKey(e.getKey()))out.put(e.getKey(),String.valueOf(e.getValue()));return out;}
    private String backupOverride="",transferBackupOverride="";
    private void showBackup(){LinearLayout c=panel("Backup & restore");paragraph(c,tr("A complete backup includes all sports, players, your name, favourite formats, settings and the current match with Undo. Keep downloaded backups private."));button(c,tr("Create or restore backup"),()->{backupOverride="";openPhone("backup",false,this::showBackup,"Back to Backup");});paragraph(c,tr("Scan the phone QR to download or choose a backup. Restoring replaces local data after review; unfinished matches return paused."));if(preferences.contains("backup_before_restore"))button(c,tr("Download previous data copy"),()->{backupOverride=preferences.getString("backup_before_restore","");openPhone("backup",false,this::showBackup,"Back to Backup");});backButton(c,"Back to Settings",this::showSettings);}
    private void previewBackupRestore(org.json.JSONObject payload,TransferClient.Session session)throws Exception{
        if(!transferSnapshotBackup)throw new IllegalArgumentException(tr("Start Backup & restore on the watch first"));
        if(!payload.getString("baseDirectory").equals(TransferClient.base(book,opponents))||!payload.getString("currentMatchId").equals(book.current().id()))throw new IllegalArgumentException(tr("Watch data changed. Start a fresh backup link."));
        final BackupArchive restored=BackupArchive.parse(payload.getString("restoreBackup"));final String before=book.encode(),namesBefore=opponents.encode();final java.util.Map<String,String> settingsSnapshot=backupSettings();final String settingsBefore=BackupArchive.create(book,opponents,settingsSnapshot);
        LinearLayout c=panel("Restore this backup?");paragraph(c,tr("Backup from ")+restored.createdAt+"\n"+restored.book.count()+tr(" saved matches\n")+restored.directory.list(true).size()+tr(" players\nYour name: ")+restored.settings.getOrDefault("my_name",tr("Me")));paragraph(c,tr("Replace all local match history, players, settings and the current match? Current local data will be kept as a recovery copy. An unfinished restored match will be paused."));
        deleteButton(c,tr("Replace data & restore"),()->{
            try{if(session.expired()||!before.equals(book.encode())||!namesBefore.equals(opponents.encode())||!settingsSnapshot.equals(backupSettings()))throw new IllegalArgumentException(tr("Watch data changed or the link expired. Receive a fresh preview."));
                SharedPreferences.Editor edit=preferences.edit();for(String k:preferences.getAll().keySet())if(BackupArchive.settingKey(k))edit.remove(k);
                for(java.util.Map.Entry<String,String> e:restored.settings.entrySet()){String k=e.getKey(),v=e.getValue();if(k.equals("last_best_of")||k.startsWith("last_best_"))edit.putInt(k,Integer.parseInt(v));else if(k.equals("match_keep_awake_v3")||k.equals("match_reminders")||k.equals("match_paused"))edit.putBoolean(k,Boolean.parseBoolean(v));else edit.putString(k,v);}
                edit.putString("match_book",restored.book.encode()).putString("opponent_book",restored.directory.encode()).putString("backup_before_restore",settingsBefore).remove(MatchDisplayPolicy.ACTIVE_MATCH).putBoolean("match_paused",!restored.book.current().complete()&&(restored.book.current().hasPoints()||restored.settings.containsKey("match_ready_id")));
                if(!edit.commit())throw new IllegalStateException(tr("Restore was not saved. Your previous data is kept."));if(live!=null)live.stop(true);book=restored.book;opponents=restored.directory;selectedSport=sportLocked()?book.current().format().sport:restored.settings.getOrDefault("selected_sport",book.current().format().sport);manualMatch=null;transfer=null;counter.match(book.current());updateDisplay();tasks.execute(()->{try{TransferClient.end(session);}catch(Exception ignored){}});message(tr("Backup restored"),book.count()+tr(" results and ")+opponents.list(true).size()+tr(" players restored. Resume your match when ready."),this::showBackup);
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
        transferBusy=true;LinearLayout content=panel("Preparing phone link");paragraph(content,tr("Creating an encrypted ten-minute link. Both devices need internet."));backButton(content,transferReturnLabel,transferReturn);
        final Dialog waiting=panel;
        tasks.execute(()->{try{TransferClient.Session created=TransferClient.create(MatchBook.decode(saved),OpponentDirectory.decode(names),selected,selectedSport,myName,backupMode?(backupCopy.isEmpty()?BackupArchive.create(MatchBook.decode(saved),OpponentDirectory.decode(names),backupSettings):backupCopy):null);onUi(()->transferCreated(created,filtered,backupMode,backupCopy,waiting));}catch(Exception error){onUi(()->{transferBusy=false;if(panel==waiting)message(tr("Transfer unavailable"),safe(error),transferReturn);});}});
    }
    private void transferCreated(TransferClient.Session created,boolean filtered,boolean backupMode,String backupCopy,Dialog waiting){transferBusy=false;transfer=created;transferBackupOverride=backupCopy;transferSnapshotFiltered=filtered;transferSnapshotBackup=backupMode;if(panel==waiting)showTransfer();}
    private String phoneTitle(){return transferView.equals("backup")?tr("Backup & restore"):transferView.equals("opponents")?tr("Edit names on phone"):transferView.equals("import")?tr("Import CSV"):tr("Results on phone");}
    private String phoneLink(){return transfer.link()+"&view="+transferView+(transferResultId.isEmpty()?"":"&match="+transferResultId);}
    private void showTransfer(){
        LinearLayout content=panel(phoneTitle());
        if(transfer==null||transfer.expired()){
            paragraph(content,tr("No active phone link. Create an encrypted link that expires after ten minutes."));
            button(content,tr("Create phone link"),()->{transfer=null;beginTransfer(transferFiltered);});
        }else{
            button(content,tr("Show phone QR"),()->showQr(phoneLink(),tr("Scan with phone"),this::showTransfer,"Back to phone link"));
            String purpose=transferView.equals("backup")?tr("Download a complete backup, or select a backup file to restore. Restore requires a separate watch confirmation."):transferView.equals("opponents")?tr("Edit saved names in your phone browser."):transferView.equals("import")?tr("Select a Keep Score CSV in your phone browser."):tr("Add or edit results, browse statistics or download CSV in your browser.");
            paragraph(content,purpose+tr("\n\nExpires ")+java.time.Instant.ofEpochMilli(transfer.expiresAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+tr(". Send edits on the phone, then receive them here."));
            Button receive=navigationButton(content,transferBusy?tr("Receiving…"):tr("Receive changes"),this::receiveTransfer);receive.setEnabled(!transferBusy);
            navigationButton(content,tr("End transfer"),()->{if(transferBusy)return;final TransferClient.Session ending=transfer;transfer=null;tasks.execute(()->{try{TransferClient.end(ending);}catch(Exception ignored){}});showTransfer();});
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
        button(content,tr("Apply changes"),()->{
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
        LinearLayout content=panel("App updates");updatePanel=true;updateText=paragraph(content,updateDescription());
        settingsButton(content,checking?tr("Checking…"):tr("Check for updates"),this::checkUpdates);
        if(latest!=null&&latest.newerThan(BuildConfig.VERSION_CODE)&&latest.supports(Build.VERSION.SDK_INT)){
            if(getPackageManager().canRequestPackageInstalls())settingsButton(content,downloading?tr("Downloading…"):tr("Download & update"),this::downloadUpdate);
            else settingsButton(content,tr("Allow app updates"),this::allowUpdates);
        }else if(!getPackageManager().canRequestPackageInstalls())settingsButton(content,tr("Set up direct updates"),this::allowUpdates);
        settingsButton(content,tr("Phone install help"),this::phoneHelp);backButton(content,"Back to Settings",this::showSettings);
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
                    setUpdateStatus(!release.supports(Build.VERSION.SDK_INT)?"New release needs a newer watch OS."
                        :release.newerThan(BuildConfig.VERSION_CODE)?"A new version is ready.":"You have the latest version.");
                    counter.available(release.newerThan(BuildConfig.VERSION_CODE)&&release.supports(Build.VERSION.SDK_INT));
                    if(updatePanel)showUpdates();
                });
            }catch(Exception error){onUi(()->{checking=false;setUpdateStatus("Check failed: "+error.getMessage());if(updatePanel)showUpdates();});}
        });
    }
    private void downloadUpdate(){
        if(BuildConfig.PLAY_DISTRIBUTION){showPlayUpdates();return;}
        if(downloading||latest==null)return;
        if(!getPackageManager().canRequestPackageInstalls()){allowUpdates();return;}
        downloading=true;persistScore();setUpdateStatus("Downloading verified update…");
        final UpdatePolicy release=latest;
        tasks.execute(()->{
            try{
                File apk=updater.download(release,p->onUi(()->setUpdateStatus("Downloading: "+p+"%")));
                onUi(()->setUpdateStatus("Verified. Requesting installation…"));
                preferences.edit().putString("install_status","Installation requested. Confirm on the watch if asked; then reopen the app.").commit();
                updater.install(apk);
                onUi(()->{downloading=false;setUpdateStatus("Installation requested. Reopen Keep Score after it finishes.");if(updatePanel)showUpdates();});
            }catch(Exception error){onUi(()->{downloading=false;setUpdateStatus("Update stopped: "+error.getMessage());if(updatePanel)showUpdates();});}
        });
    }
    private String safe(Exception error){String message=error.getMessage();return message==null?error.getClass().getSimpleName():ui.status(message);}
    private void onUi(Runnable work){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())work.run();});}
    private void setUpdateStatus(String value){updateStatus=value;preferences.edit().putString("update_status",value).apply();if(updatePanel&&updateText!=null)updateText.setText(updateDescription());}
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
        settingsButton(help,tr("Copy command"),()->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Update setup","appops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow"));
        });settingsButton(help,tr("Phone install help"),this::phoneHelp);backButton(help,"Back to App updates",this::showUpdates);
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
        settingsButton(content,tr("Open Google Play"),this::openPlayStore);
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
        String text=diagnosticText(false);
        paragraph(details,text);settingsButton(details,tr("Send to phone"),this::showDiagnosticTransfer);
        settingsButton(details,tr("Copy details"),()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Keep Score diagnostics",text)));
        backButton(details,"Back to Settings",this::showSettings);
    }
    private String diagnosticText(boolean compact){
        String trace=preferences.getString("lifecycle_trace","");
        if(compact)trace=AppDiagnostics.tailLines(trace,1800);
        String crash=preferences.getString("last_crash",tr("No recorded Java crash"));
        if(compact&&crash.length()>1400)crash=crash.substring(0,1400);
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
            +"\n\n"+updateDescription()+"\n\n"+tr(preferences.getString("diagnostic",""))
            +tr("\n\nLast crash:\n")+crash+tr("\n\nProcess exit reasons:\n")+AppDiagnostics.recentExits(this)+tr("\n\nLifecycle trace:\n")+trace;
        return text;
    }
    private void showDiagnosticTransfer(){
        try{showQr(DiagnosticTransfer.link(BuildConfig.SITE_URL,diagnosticText(true)),tr("Scan with phone"),this::showDiagnostics);}
        catch(Exception error){message(tr("Diagnostic transfer"),safe(error),this::showDiagnostics);}
    }
    private void showQr(String link,String title,Runnable back){
        showQr(link,title,back,"Back to Diagnostics");
    }
    void showQr(String link,String title,Runnable back,String backLabel){
        try{
            java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L);
            hints.put(com.google.zxing.EncodeHintType.MARGIN,2);hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");
            int size=Math.round(Math.min(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels)*.64f);
            com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,com.google.zxing.BarcodeFormat.QR_CODE,size,size,hints);
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.RGB_565);
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            closePanel();panel=new Dialog(this,R.style.WatchPanel);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
            View qr=new View(this){
                final android.graphics.Paint p=new android.graphics.Paint();
                @Override protected void onDraw(android.graphics.Canvas canvas){
                    canvas.drawColor(Color.BLACK);p.setColor(Color.WHITE);p.setTextAlign(android.graphics.Paint.Align.CENTER);p.setTextSize(getWidth()*.045f);
                    canvas.drawText(tr(title),getWidth()*.5f,getHeight()*.12f,p);
                    canvas.drawBitmap(bitmap,(getWidth()-bitmap.getWidth())/2f,(getHeight()-bitmap.getHeight())/2f,p);
                    p.setColor(0xff76cfff);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1));
                    canvas.drawRoundRect(new android.graphics.RectF(getWidth()*.16f,getHeight()*.845f,getWidth()*.84f,getHeight()*.935f),dp(12),dp(12),p);
                    p.setStyle(android.graphics.Paint.Style.FILL);canvas.drawText(tr(backLabel),getWidth()*.5f,getHeight()*.90f,p);
                }
                @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_UP&&event.getY()>getHeight()*.83f)back.run();return true;}
            };
            panelPage=backLabel.equals("Back to Diagnostics")?"Diagnostic QR":backLabel.equals("Back to Live scoreboard")?"Audience QR":"Phone QR";panel.setContentView(swipeContainer(qr,panel));panel.show();panel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));panel.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);panel.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);trackPanelDismiss();bindBack(back);updateDisplay();
            final Window qrWindow=panel.getWindow();qr.postDelayed(()->qrWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON),60000L);
        }catch(Exception error){message("QR unavailable",safe(error),back);}
    }
    private LinearLayout panel(String title){
        return panel(title,null);
    }
    private LinearLayout panel(String title,String scrollKey){return panel(title,scrollKey,false);}
    private LinearLayout panel(String title,String scrollKey,boolean playerTitle){
        closePanel();
        panel=new Dialog(this,R.style.WatchPanel);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff080d17);scroll.setClipToPadding(false);
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();boolean round=getResources().getConfiguration().isScreenRound();
        int horizontal=round?Math.round(metrics.widthPixels*.095f):dp(12),top=round?Math.round(metrics.heightPixels*.105f):dp(14);
        scroll.setPadding(horizontal,top,horizontal,round?Math.round(metrics.heightPixels*.14f):dp(18));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        if(!playerTitle&&title.equals("Choose sport")){TextView brand=paragraph(content,"Keep Score");brand.setTextSize(10);brand.setTextColor(0xff9daec4);}
        else sportBadge(content,sharedArea?null:selectedSport);
        TextView heading=paragraph(content,playerTitle?title:tr(title));heading.setTextSize(17);heading.setTextColor(0xffcdeaff);
        panelShortcut=(playerTitle||!title.equals("Menu")&&!title.equals("Choose sport")&&!title.endsWith("?"))&&book.current().format().sport.equals(selectedSport)&&(sessionOngoing()||needsResume());
        panelScroll=scroll;panelScrollKey=scrollKey;
        panelPage=title;panel.setContentView(swipeContainer(scroll,panel));panel.show();
        panel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        panel.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        trackPanelDismiss();
        bindBack(this::closePanel);
        if(scrollKey!=null){final int position=scrollPositions.containsKey(scrollKey)?scrollPositions.get(scrollKey):0;scroll.post(()->scroll.scrollTo(0,position));}
        updateDisplay();
        return content;
    }
    private void trackPanelDismiss(){
        final Dialog shown=panel;
        Runnable releaseBack=registerPanelBack(shown);
        panel.setOnDismissListener(d->{releaseBack.run();if(panel==shown){rememberPanelScroll();updatePanel=false;updateText=null;panel=null;panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";updateDisplay();}});
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
    private void section(LinearLayout content,String text){TextView label=paragraph(content,text.toUpperCase(java.util.Locale.ROOT));label.setTextSize(10);label.setTextColor(0xff9daec4);label.setPadding(0,dp(7),0,dp(3));}
    private void sportBadge(LinearLayout content,String sport){
        TextView badge=paragraph(content,sport==null?"Keep Score":tr(MatchFormat.name(sport)));badge.setTextSize(12);badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);badge.setTextColor(sport==null?0xffd9c6ff:SportIcons.accent(sport));badge.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(12));shape.setColor(sport==null?0xff241c32:(SportIcons.accent(sport)&0x00ffffff)|0x18000000);badge.setBackground(shape);
        if(sport!=null){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));badge.setCompoundDrawables(icon,null,null,null);badge.setCompoundDrawablePadding(dp(5));}
        badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));badge.setContentDescription(sport==null?tr("Keep Score, shared across all sports"):tr("Current sport: ")+tr(MatchFormat.name(sport)));
    }
    private void sportButton(Button button,String sport){
        int accent=SportIcons.accent(sport);GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(15));shape.setColor((accent&0x00ffffff)|0x18000000);shape.setStroke(dp(1),(accent&0x00ffffff)|0x66000000);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff687589,accent}));button.setCompoundDrawablesWithIntrinsicBounds(new SportIcons(sport,dp(22)),null,null,null);button.setCompoundDrawablePadding(dp(8));
    }
    private Button choiceButton(LinearLayout content,String text,boolean selected,Runnable action){
        Button option=styledButton(content,(selected?"✓ ":"")+text,action,sharedArea?ButtonKind.SETTINGS:ButtonKind.NAVIGATION);option.setContentDescription(text+(selected?tr(", selected"):tr(", select")));option.setSelected(selected);
        if(selected){GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(15));shape.setColor(sharedArea?0xff363636:0xff123047);shape.setStroke(dp(1),sharedArea?0xffa7b6c9:SportIcons.accent(selectedSport));option.setBackgroundTintList(null);option.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));}
        return option;
    }
    Button button(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.MATCH);}
    Button navigationButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.NAVIGATION);}
    private Button personButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.PERSON);}
    private Button settingsButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.SETTINGS);}
    Button deleteButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.DELETE);}
    Button backButton(LinearLayout content,String text,Runnable action){if(panelShortcut){panelShortcut=false;styledButton(content,tr("Scoreboard"),this::closePanel,ButtonKind.SHORTCUT);}bindBack(action);return styledButton(content,tr(text),action,ButtonKind.BACK);}
    private Button styledButton(LinearLayout content,String text,Runnable action,ButtonKind kind){
        boolean settings=kind==ButtonKind.SETTINGS,delete=kind==ButtonKind.DELETE||kind==ButtonKind.SCORE_OPPONENT,back=kind==ButtonKind.BACK,person=kind==ButtonKind.PERSON,navigation=kind==ButtonKind.NAVIGATION,shortcut=kind==ButtonKind.SHORTCUT,plain=back||navigation||shortcut;
        Button button=new Button(this);button.setText(text);button.setTextSize(12);button.setAllCaps(false);button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));
        button.setPadding(dp(10),dp(8),dp(10),dp(8));button.setBackgroundTintList(null);
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(person?9:15));
        ColorStateList colours=new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_pressed},new int[]{}},
            new int[]{plain?0x00000000:person?0xff30203d:settings?0xff292929:delete?0xff682039:0xff123f70,plain?0xff123a52:person?0xff77508e:settings?0xff666666:delete?0xffef4260:0xff2489f5,plain?0x00000000:person?0xff4e325f:settings?0xff444444:delete?0xffb82e4c:0xff1668cb});
        shape.setColor(colours);if(back||shortcut)shape.setStroke(dp(1),0xff76cfff);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));
        if(!plain){button.setBackgroundTintList(colours);button.setBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC_IN);}
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{plain?0xff5f91ad:person?0xffac94ba:settings?0xffb8b8b8:delete?0xfff1bac7:0xffa5cdf7,plain?0xffa6e2ff:person?0xfff7eafe:Color.WHITE}));
        if(person){button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);android.graphics.drawable.Drawable icon=getDrawable(R.drawable.ic_person);icon.setBounds(0,0,dp(18),dp(18));button.setCompoundDrawables(icon,null,null,null);button.setCompoundDrawablePadding(dp(8));}
        if(navigation){button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);android.graphics.drawable.Drawable icon=getDrawable(R.drawable.ic_navigation_arrow);icon.setBounds(0,0,dp(16),dp(16));button.setCompoundDrawables(null,null,icon,null);button.setCompoundDrawablePadding(dp(4));}
        button.setOnClickListener(v->action.run());LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);position.bottomMargin=dp(8);content.addView(button,position);
        return button;
    }
    private void resultCard(LinearLayout content,MatchResult result,Runnable open){
        boolean meWon=result.winner()==0;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(16));shape.setColor(meWon?0xff103b68:0xff551c30);shape.setStroke(dp(2),meWon?0xff2489f5:0xfff34b65);
        card.setBackground(open==null?shape:new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));
        TextView reference=null;
        if(open!=null){reference=paragraph(card,ui.scoreTerms(result.title()));reference.setTextSize(11);reference.setTextColor(0xffedf4ff);}
        paragraph(card,tr(result.format.name())+" · "+tr(result.format.mode())+"\n"+(result.format.doubles?teamName(result.id,0)+tr(" vs ")+teamName(result.id,1):tr("vs ")+opponentName(result.id)));
        TextView score=paragraph(card,result.format.doubles?tr("Our team ")+result.gamesWon(0)+"–"+result.gamesWon(1)+tr(" Opponents"):ownName()+" "+result.gamesWon(0)+"–"+result.gamesWon(1)+" "+opponentName(result.id));score.setTextSize(17);score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);score.setTextColor(Color.WHITE);
        TextView details=paragraph(card,tr("Best of ")+result.bestOf+" · "+ui.scoreTerms(result.outcomeText())+(open==null?"\n"+ui.scoreTerms(result.timingText()):"")+tr("\nDuration: ")+ui.scoreTerms(result.durationText())+tr("\nMatch ")+result.number+(open==null?"\n\n"+ui.scoreTerms(result.gamesText()):""));details.setTextSize(12);details.setTextColor(0xffedf4ff);
        if(open!=null){
            card.setFocusable(true);card.setClickable(true);card.setContentDescription(ui.scoreTerms(result.title())+". "+ui.scoreTerms(result.summary())+tr(". Open match result."));
            reference.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);score.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);details.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.setOnClickListener(v->open.run());
        }
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);position.bottomMargin=dp(12);content.addView(card,position);
    }
    void closePanel(){rememberPanelScroll();if(panel!=null){panel.dismiss();panel=null;}panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";updatePanel=false;updateText=null;updateDisplay();}
}
