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
    private MatchBook book;
    private OpponentDirectory opponents;
    private String filterOpponent;
    private LocalDate filterFrom,filterTo;
    private EditText nameInput;
    private TransferClient.Session transfer;
    private boolean transferBusy;
    private ScoreView counter;
    private UpdateClient updater;
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
    private Runnable playersReturn=this::showMenu,settingsReturn=this::showMenu;
    private String playersReturnLabel="Back to Menu",settingsReturnLabel="Back to Menu";
    private String newMatchOpponent="";
    private int newMatchBestOf=3,resultsPage;
    private String selectedSport="squash",newMatchPartner="",newMatchOther="";
    private MatchFormat newMatchFormat=MatchFormat.squash();
    private int manualEditingIndex=-1;
    private int filterMode=-1,newFirstServer=-1,newFirstReceiver=-1;
    private Runnable newMatchReturn=this::closePanel;
    private Runnable resultsReturn=this::showMenu;
    private String resultsReturnLabel="Back to Menu";
    private ManualMatchEntry manualMatch;
    private LocalDate manualDate;
    private Runnable transferReturn=()->showResults(resultsPage);
    private String transferReturnLabel="Back to Results",transferView="results";
    private boolean transferFiltered;
    private boolean transferSnapshotFiltered;
    private ScrollView panelScroll;
    private String panelScrollKey;
    private final java.util.LinkedHashMap<String,Integer> scrollPositions=new java.util.LinkedHashMap<>();
    private boolean resumed,ambient;
    private final LifecycleRegistry lifecycle=new LifecycleRegistry(this);
    private AmbientLifecycleObserver ambientObserver;
    private boolean ambientUnavailable;
    @Override public Lifecycle getLifecycle(){return lifecycle;}

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        preferences=getSharedPreferences("squash",MODE_PRIVATE);
        String savedBook=preferences.getString("match_book",null);
        try{book=savedBook==null?MatchBook.migrateLegacy(UUID.randomUUID().toString(),preferences.getString("score","1|")):MatchBook.decode(savedBook);}
        catch(Exception damaged){
            if(savedBook!=null)preferences.edit().putString("match_book_recovery",savedBook).commit();
            book=new MatchBook(new MatchState(UUID.randomUUID().toString(),preferences.getInt("last_best_of",3)));
            preferences.edit().putString("diagnostic","Saved match data could not be read. A recovery copy was retained; do not clear app data.").apply();
        }
        if(savedBook!=null&&savedBook.startsWith("B1:")&&!preferences.contains("match_book_v1_backup"))preferences.edit().putString("match_book_v1_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B2:")&&!preferences.contains("match_book_v2_backup"))preferences.edit().putString("match_book_v2_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B4:")&&!preferences.contains("match_book_v4_backup"))preferences.edit().putString("match_book_v4_backup",savedBook).commit();
        if(savedBook!=null&&savedBook.startsWith("B3:")&&!preferences.contains("match_book_v3_backup"))preferences.edit().putString("match_book_v3_backup",savedBook).commit();
        if(preferences.getString("opponent_book","").startsWith("O1:")&&!preferences.contains("opponent_book_v1_backup"))preferences.edit().putString("opponent_book_v1_backup",preferences.getString("opponent_book","")).commit();
        try{opponents=OpponentDirectory.decode(preferences.getString("opponent_book",null));}catch(Exception damaged){preferences.edit().putString("opponent_book_recovery",preferences.getString("opponent_book","")).putString("diagnostic","Saved opponent data could not be read. A recovery copy was retained.").commit();opponents=new OpponentDirectory();}
        persistScore();counter=new ScoreView(this,book.current(),this);setContentView(counter);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        AppDiagnostics.record(this,"Activity create");
        updater=new UpdateClient(this);
        updateStatus=preferences.getString("update_status",updateStatus);
        selectedSport=preferences.getString("selected_sport",book.current().format().sport);if(!java.util.Arrays.asList(MatchFormat.SPORTS).contains(selectedSport))selectedSport="squash";
        if(sportLocked())selectedSport=book.current().format().sport;
        if(Intent.ACTION_MAIN.equals(getIntent().getAction()))showSports();
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
        AppDiagnostics.record(this,"Activity resume; ambient="+ambient+" paused="+preferences.getBoolean("match_paused",false));
        requestMatchNotification();
    }
    private void requestMatchNotification(){
        if(sessionOngoing()&&resumed&&Build.VERSION.SDK_INT>=33&&!preferences.getBoolean("asked_match_notification",false)){
            preferences.edit().putBoolean("asked_match_notification",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71);
        }
    }
    @Override protected void onPause(){AppDiagnostics.record(this,"Activity pause; page="+panelPage+" screenFlag="+((getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)+" ambient="+ambient+" finishing="+isFinishing());persistScore();resumed=false;updateDisplay();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);super.onPause();}
    @Override protected void onStop(){lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);AppDiagnostics.record(this,"Activity stop; finishing="+isFinishing()+" changingConfig="+isChangingConfigurations());super.onStop();}
    @Override protected void onDestroy(){lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);AppDiagnostics.record(this,"Activity destroy; finishing="+isFinishing());tasks.shutdownNow();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);closePanel();updateDisplay();AppDiagnostics.record(this,"Returned through match shortcut");}
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){super.onRequestPermissionsResult(code,names,grants);if(code==71){AppDiagnostics.record(this,"Notification permission result "+(grants.length>0?grants[0]:-1));updateDisplay();}}
    private boolean sessionOngoing(){return MatchDisplayPolicy.ongoing(book.current(),preferences.getString(MatchDisplayPolicy.ACTIVE_MATCH,""),preferences.getBoolean("match_paused",false));}
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
        counter.setKeepScreenOn(awake);counter.session(ongoing,needsResume());counter.opponent(book.current().format().doubles?teamName(book.current().id(),1):opponents.nameFor(book.current().id()));counter.players(opponents.roleName(book.current().id(),1),opponents.roleName(book.current().id(),0),opponents.roleName(book.current().id(),2));
        if(awake)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(!resumed&&panel!=null)panel.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent service=new Intent(this,MatchSessionService.class);
        if(!ongoing)stopService(service);
        else if(resumed){try{startForegroundService(service);}catch(Exception error){AppDiagnostics.record(this,"Session start failed: "+safe(error));}}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void action(int target){
        switch(target){
            case TouchTargets.BLUE:case TouchTargets.RED:
                if(needsResume()){showMatchControls();return;}
                if(book.add(target)){
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
            case TouchTargets.MENU:scoreMenu=true;selectSport(book.current().format().sport);showMenu();break;
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
        if(!allowSport(selectedSport))return;
        sharedArea=false;
        LinearLayout menu=panel("Menu");
        boolean here=book.current().format().sport.equals(selectedSport)&&(hasCurrentMatch()||scoreMenu);
        if(here)button(menu,"Current match",this::showMatchControls);
        else{button(menu,"New "+MatchFormat.name(selectedSport)+" match",()->showNewMatch(this::showMenu));if(hasCurrentMatch()&&!book.current().complete())navigationButton(menu,"Resume "+book.current().format().name(),this::showMatchControls);}
        navigationButton(menu,"Results ("+sportResults(-1).size()+")",()->openResults(this::showMenu,"Back to Menu"));
        navigationButton(menu,"Statistics",this::showStatistics);
        section(menu,"Sport");
        navigationButton(menu,"Scoring rules",this::showSportRules);
        navigationButton(menu,"Change sport",this::showSports);
        section(menu,"App · all sports");
        personButton(menu,"Players",()->openPlayers(this::showMenu,"Back to Menu"));
        settingsButton(menu,"Settings",()->openSettings(this::showMenu,"Back to Menu"));
        if(here)backButton(menu,"Back to score",this::closePanel);
        else backButton(menu,"Back to sports",this::showSports);
    }
    private boolean hasCurrentMatch(){return book.current().hasPoints()||book.current().complete()||book.currentStartedAt()>0||sessionOngoing()||needsResume()||book.current().id().equals(preferences.getString("match_ready_id",""));}
    private boolean sportLocked(){return !book.current().complete()&&hasCurrentMatch();}
    private boolean allowSport(String sport){if(sportLocked()&&!sport.equals(book.current().format().sport)){showSportLocked();return false;}return true;}
    private void showSportLocked(){
        selectedSport=book.current().format().sport;sharedArea=false;LinearLayout content=panel("Match still running");
        paragraph(content,"Finish or cancel your "+book.current().format().name()+" match before choosing another sport. Pausing keeps the sport locked.");
        button(content,"Current match",this::showMatchControls);deleteButton(content,"Cancel match",this::showCancelMatch);backButton(content,"Back to sports",this::showSports);
    }
    private void showMatchControls(){
        MatchState match=book.current();selectSport(match.format().sport);sharedArea=false;LinearLayout menu=panel("Current match");
        boolean ongoing=sessionOngoing();
        if(!match.complete())button(menu,ongoing?"Pause match":needsResume()?"Resume match":"Start match",()->{setSession(!ongoing,ongoing);closePanel();updateDisplay();});
        else if(book.result(match.id())!=null)button(menu,"View result",()->showResult(match.id(),resultsPage,this::showMatchControls,"Back to Current match"));
        paragraph(menu,match.format().name()+" · "+match.format().description());
        paragraph(menu,(match.complete()?"Finished":ongoing?"Playing":needsResume()?"Paused / ready to resume":"Ready to play")+" · Best of "+match.bestOf()+"\nMe "+match.gamesWon(0)+"–"+match.gamesWon(1)+" "+opponentName(match.id()));
        if(!match.complete())navigationButton(menu,"Enter set score",this::enterCurrentSet);
        navigationButton(menu,"Match details",this::showCurrentMatch);
        if(match.complete())button(menu,"New match",()->showNewMatch(this::showMatchControls));
        else{navigationButton(menu,"Match options",this::showMatchOptions);deleteButton(menu,"Cancel match",this::showCancelMatch);}
        backButton(menu,"Back to Menu",this::showMenu);
    }
    private void showMatchOptions(){
        MatchState match=book.current();if(match.complete()){showMatchControls();return;}
        LinearLayout menu=panel("Match options","match-options:"+match.id());
        section(menu,"Setup & players");
        if(match.format().doubles)personButton(menu,"Players",()->showTeam(match.id(),match.format(),this::showMatchOptions));else personButton(menu,"Opponent: "+opponentName(match.id()),()->showOpponentPicker(match.id(),false,0,this::showMatchOptions));
        navigationButton(menu,"Serving indicator",this::showServing);
        navigationButton(menu,"Match length: Best of "+match.bestOf(),this::showMatchFormat);
        paragraph(menu,"Scoring: "+match.format().description()+"\nChoose different scoring rules when creating a new match.");
        section(menu,"Finish or replace");
        navigationButton(menu,"New match",()->showNewMatch(this::showMatchOptions));
        navigationButton(menu,"Save retirement result",this::showEndMatch);
        paragraph(menu,"Retirement saves a win/loss. Cancel match discards this match without a result.");
        deleteButton(menu,"Cancel match",this::showCancelMatch);
        backButton(menu,"Back to Current match",this::showMatchControls);
    }
    private void showSettings(){
        LinearLayout menu=panel("Settings","settings");paragraph(menu,"Squash Tap v"+BuildConfig.VERSION_NAME);
        settingsButton(menu,"Check for updates",()->{showUpdates();checkUpdates();});
        boolean awake=preferences.getBoolean("match_keep_awake_v3",true);
        settingsButton(menu,"Keep screen on: "+(awake?"ON":"OFF"),this::chooseScreenPolicy);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            settingsButton(menu,"Enable match shortcut",()->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71));
        settingsButton(menu,"Diagnostics",this::showDiagnostics);backButton(menu,settingsReturnLabel,settingsReturn);
    }
    private void openSettings(Runnable back,String label){sharedArea=true;settingsReturn=back;settingsReturnLabel=label;showSettings();}
    private void openPlayers(Runnable back,String label){sharedArea=true;playersReturn=back;playersReturnLabel=label;showOpponents(0);}
    private void chooseScreenPolicy(){
        LinearLayout content=panel("Screen during play");boolean awake=preferences.getBoolean("match_keep_awake_v3",true);
        paragraph(content,"Applies only to the visible scoreboard during active play. Menus and paused matches allow normal sleep.");
        choiceButton(content,"Keep screen on",awake,()->saveScreenPolicy(true));choiceButton(content,"Allow screen to sleep",!awake,()->saveScreenPolicy(false));
        backButton(content,"Cancel",this::showSettings);
    }
    private void saveScreenPolicy(boolean value){preferences.edit().putBoolean("match_keep_awake_v3",value).commit();updateDisplay();showSettings();}
    private void showMatchFormat(){
        MatchState match=book.current();LinearLayout content=panel("Match length");
        paragraph(content,"Current: Best of "+match.bestOf());
        for(int value:match.format().bestOptions()){
            final int choice=value;
            Button option=choiceButton(content,"Best of "+value+" · first to "+(value/2+1),value==match.bestOf(),()->{
                if(choice==match.bestOf()){showMatchOptions();return;}
                if(book.changeBestOf(choice)){
                    preferences.edit().putInt("last_best_of",choice).apply();
                    if(afterMatchChange())showMatchOptions();
                }
            });
            option.setEnabled(value==match.bestOf()||match.canChangeBestOf(value));
        }
        paragraph(content,"Changes keep your points, set scores and Undo history.");
        if(match.complete())paragraph(content,"This match is finished. Start a new match to choose a different format.");
        else if(match.format().finalTie&&match.hasPoints())paragraph(content,"Match length is locked after play starts when a deciding match tie-break is selected.");
        else if(match.bestOf()==5&&!match.canChangeBestOf(3))paragraph(content,"A player has already won two sets. Start a new match to use Best of 3.");
        backButton(content,"Back to Match options",this::showMatchOptions);
    }
    private boolean afterMatchChange(){
        if(book.current().complete()&&(preferences.contains(MatchDisplayPolicy.ACTIVE_MATCH)||preferences.getBoolean("match_paused",false)))setSession(false,false);
        boolean saved=persistScore();counter.match(book.current());updateDisplay();
        if(!saved){LinearLayout warning=panel("Save failed");paragraph(warning,"The watch did not confirm saving this change. Keep the app open and free storage before retrying.");button(warning,"Retry save",()->{if(persistScore())closePanel();});}
        return saved;
    }
    private void undoPoint(){
        undoPoint(this::closePanel);
    }
    private void undoPoint(Runnable cancel){
        if(book.current().undoEnteredSet()){
            final String before=book.encode();LinearLayout content=panel("Undo entered set?");paragraph(content,"This set was entered as a final score. Its point order was not recorded, so Undo removes the whole entered set. Other sets are kept.");
            button(content,"Undo entered set",()->{if(!before.equals(book.encode())){message("Match changed","Review the match before undoing.",this::showMatchControls);return;}if(book.undo()&&afterMatchChange())closePanel();});backButton(content,"Cancel",cancel);
        }else if(book.undo()&&afterMatchChange())closePanel();
    }
    private LinearLayout manualPanel(String title){LinearLayout content=panel(title);panel.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);return content;}
    private void showAddPastMatch(){choosePastFormat();}
    private void chooseSetWinner(String title,String opponent,int oldMe,int oldOther,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        MatchFormat f=manualMatch==null?book.current().format():manualMatch.format;int index=manualMatch==null?(book.current().nextReady()?book.current().gameCount():book.current().gameCount()-1):manualEditingIndex>=0?manualEditingIndex:manualMatch.size();
        boolean tie=manualMatch==null?book.current().matchTie(index):manualMatch.matchTie(index);
        if(!f.legacy()){chooseGenericScore(title,opponent,f,tie,oldMe,oldOther,apply,back);return;}
        LinearLayout content=manualPanel(title);paragraph(content,"Who won this set?");
        if(oldMe+oldOther>0)ManualEntryUi.score(content,"Score to enter",opponent,oldMe,oldOther,null);
        int loser=oldMe+oldOther==0?7:Math.min(oldMe,oldOther);
        button(content,"Me won",()->showSetScoreWheel(title,opponent,0,loser,apply,back));
        styledButton(content,"Opponent won",()->showSetScoreWheel(title,opponent,1,loser,apply,back),ButtonKind.SCORE_OPPONENT);
        backButton(content,"Cancel",back);
    }
    private void showSetScoreWheel(String title,String opponent,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);paragraph(content,(winner==0?"Me":"Opponent")+" won · choose the losing score");
        ManualEntryUi.ScoreCard preview=ManualEntryUi.score(content,"Final set score",opponent,0,0,null);
        NumberPicker wheel=ManualEntryUi.wheel(content,"Losing score",0,(ScoreState.MAX_RALLIES-2)/2,initial);
        LinearLayout controls=ManualEntryUi.row(content);Button minus=button(controls,"−",()->{}),plus=button(controls,"+",()->{});ManualEntryUi.equal(minus,plus);
        Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);preview.scores(winner==0?high:low,winner==0?low:high);minus.setEnabled(low>0);plus.setEnabled(low<wheel.getMaxValue());};
        minus.setContentDescription("Decrease losing score");plus.setContentDescription("Increase losing score");minus.setOnClickListener(v->{wheel.setValue(Math.max(0,wheel.getValue()-1));refresh.run();});plus.setOnClickListener(v->{wheel.setValue(Math.min(wheel.getMaxValue(),wheel.getValue()+1));refresh.run();});wheel.setOnValueChangedListener((p,a,b)->refresh.run());refresh.run();
        button(content,manualMatch!=null?"Use set score":"Review set score",()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);apply.accept(winner==0?high:low,winner==0?low:high);});
        paragraph(content,"Quick scores · Me–Opponent");
        for(int[] presets:new int[][]{{0,5},{7,9}}){LinearLayout row=ManualEntryUi.row(content);Button[] buttons=new Button[2];for(int i=0;i<2;i++){final int low=presets[i],high=ManualMatchEntry.winningScore(low);buttons[i]=button(row,(winner==0?high:low)+"–"+(winner==0?low:high),()->{wheel.setValue(low);refresh.run();});}ManualEntryUi.equal(buttons[0],buttons[1]);}
        button(content,"Change winner",()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(low);chooseSetWinner(title,opponent,winner==0?high:low,winner==0?low:high,apply,back);});
        backButton(content,"Cancel",back);
    }
    private void enterCurrentSet(){
        manualMatch=null;MatchState match=book.current();if(match.complete()){showMatchControls();return;}
        final String before=book.encode();int number=match.nextReady()?match.gameCount()+1:match.gameCount();int me=match.nextReady()?0:match.game().blue(),other=match.nextReady()?0:match.game().red();
        chooseSetWinner("Set "+number+" score",opponentName(match.id()),me,other,(a,b)->{
            if(!before.equals(book.encode())){message("Match changed","Review the current match before entering a set.",this::showMatchControls);return;}
            LinearLayout content=manualPanel("Save entered set?");ManualEntryUi.score(content,"Set "+number,opponentName(book.current().id()),a,b,null);
            paragraph(content,(me+other>0?"This replaces the current set's "+me+"–"+other+" score. Other sets are kept. ":"")+"Only the final score is recorded; point order and finish time stay unknown.");
            if(book.currentStartedAt()==0)paragraph(content,"Saving will use the current date and time as the match start.");
            button(content,"Save set score",()->{
                if(!before.equals(book.encode())){message("Match changed","Review the match before saving.",this::showMatchControls);return;}
                if(book.enterCompletedSet(a,b)&&afterMatchChange()){
                    if(book.current().complete()){if(book.result(book.current().id())!=null)showResult(book.current().id(),0);else if(!book.currentDeleted())showSavePending();else showMatchControls();}
                    else showEnteredSetSaved(number,a,b);
                }
            });backButton(content,"Cancel",this::showMatchControls);
        },this::showMatchControls);
    }
    private void showEnteredSetSaved(int number,int me,int other){
        LinearLayout content=manualPanel("Set saved");ManualEntryUi.score(content,"Set "+number,opponentName(book.current().id()),me,other,null);
        button(content,"Score next set live",()->{if(book.current().complete()){showMatchControls();return;}if(book.current().nextReady())book.nextGame();setSession(true,false);if(afterMatchChange())closePanel();});
        navigationButton(content,"Enter next set score",this::enterCurrentSet);backButton(content,"Back to Current match",this::showMatchControls);
    }
    private String manualOpponent(ManualMatchEntry draft){OpponentDirectory.Entry e=opponents.get(draft.opponentId());return e==null?"Opponent":e.name;}
    private void showManualMatch(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}
        LinearLayout content=manualPanel("Add past match");paragraph(content,"Add a separate completed match to history.");
        if(draft.format.legacy()){LinearLayout formats=ManualEntryUi.row(content);Button bo3=button(formats,"Best of 3",()->{draft.chooseFormat(3);showManualMatch();}),bo5=button(formats,"Best of 5",()->{draft.chooseFormat(5);showManualMatch();});ManualEntryUi.equal(bo3,bo5);bo3.setEnabled(draft.bestOf()!=3&&draft.canChooseFormat(3));bo5.setEnabled(draft.bestOf()!=5&&draft.canChooseFormat(5));}else for(int option:draft.format.bestOptions()){Button length=button(content,"Best of "+option,()->{draft.chooseFormat(option);showManualMatch();});length.setEnabled(option!=draft.bestOf()&&draft.canChooseFormat(option));}
        paragraph(content,draft.format.name()+" · "+draft.format.description()+" · Best of "+draft.bestOf());
        personButton(content,"Opponent: "+(draft.opponentId().isEmpty()?"No name":manualOpponent(draft))+" (optional)",()->showManualOpponent(0));
        if(draft.format.doubles){personButton(content,"Partner: "+playerName(draft.partnerId),()->pickPlayer("Partner",id->{draft.partnerId=id;showManualMatch();},this::showManualMatch,0));personButton(content,"Opponent 2: "+playerName(draft.otherId),()->pickPlayer("Opponent 2",id->{draft.otherId=id;showManualMatch();},this::showManualMatch,0));}
        button(content,"Date: "+draft.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),()->{manualDate=draft.date();showManualDate();});
        for(int i=0;i<draft.size();i++){final int index=i;ManualEntryUi.score(content,"Set "+(i+1)+" · tap to edit",manualOpponent(draft),draft.points(i,0),draft.points(i,1),()->editManualSet(draft,index));}
        if(!draft.complete())button(content,"Add set "+(draft.size()+1),()->editManualSet(draft,draft.size()));
        else paragraph(content,"Match complete. Check the set scores, opponent and date before saving.");
        if(draft.size()>0)deleteButton(content,"Remove last set",()->{draft.removeLast();showManualMatch();});
        paragraph(content,"Match times are left as Not recorded. You are entering final set scores, not a rally log.");
        Button save=button(content,"Save match result",()->saveManualMatch(draft));save.setEnabled(draft.complete());
        backButton(content,"Cancel",()->{manualMatch=null;showResults(resultsPage);});
    }
    private void editManualSet(ManualMatchEntry draft,int index){
        manualEditingIndex=index;int me=index<draft.size()?draft.points(index,0):0,other=index<draft.size()?draft.points(index,1):0;
        chooseSetWinner("Set "+(index+1)+" score",manualOpponent(draft),me,other,(a,b)->{if(manualMatch!=draft)return;try{draft.setScore(index,a,b);showManualMatch();}catch(Exception invalid){message("Check set order",safe(invalid),this::showManualMatch);}},this::showManualMatch);
    }
    private void showManualOpponent(int requestedPage){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));LinearLayout content=manualPanel("Choose opponent");
        personButton(content,"No name",()->{draft.opponentId("");showManualMatch();});
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry e=entries.get(i);personButton(content,e.name,()->{draft.opponentId(e.id);showManualMatch();});}
        if(page>0)navigationButton(content,"Previous names",()->showManualOpponent(page-1));if((page+1)*10<entries.size())navigationButton(content,"More names",()->showManualOpponent(page+1));paragraph(content,"Names are optional. Add them from Opponents → Edit names on phone.");backButton(content,"Cancel",this::showManualMatch);
    }
    private void showManualDate(){
        final ManualMatchEntry draft=manualMatch;if(draft==null){showResults(resultsPage);return;}LinearLayout content=manualPanel("Match date");paragraph(content,manualDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        button(content,"Today",()->{draft.date(LocalDate.now(),LocalDate.now());showManualMatch();});button(content,"Yesterday",()->{draft.date(LocalDate.now().minusDays(1),LocalDate.now());showManualMatch();});
        button(content,"Day: "+manualDate.getDayOfMonth(),()->manualDateWheel("Day",1,manualDate.lengthOfMonth(),manualDate.getDayOfMonth(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),manualDate.getMonthValue(),v)));
        button(content,"Month: "+manualDate.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()),()->manualDateWheel("Month",1,12,manualDate.getMonthValue(),v->manualDate=ManualMatchEntry.changeDate(manualDate,manualDate.getYear(),v,manualDate.getDayOfMonth())));
        button(content,"Year: "+manualDate.getYear(),()->manualDateWheel("Year",1900,LocalDate.now().getYear(),manualDate.getYear(),v->manualDate=ManualMatchEntry.changeDate(manualDate,v,manualDate.getMonthValue(),manualDate.getDayOfMonth())));
        button(content,"Use date",()->{try{draft.date(manualDate,LocalDate.now());showManualMatch();}catch(Exception invalid){message("Check date",safe(invalid),this::showManualDate);}});backButton(content,"Cancel",this::showManualMatch);
    }
    private void manualDateWheel(String title,int min,int max,int value,java.util.function.IntConsumer apply){
        LinearLayout content=manualPanel(title);NumberPicker wheel=ManualEntryUi.wheel(content,title,min,max,value);if(title.equals("Month")){String[] months=new String[12];for(int i=1;i<=12;i++)months[i-1]=java.time.Month.of(i).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault());wheel.setDisplayedValues(months);}
        button(content,"Use "+title.toLowerCase(java.util.Locale.ROOT),()->{apply.accept(wheel.getValue());showManualDate();});backButton(content,"Cancel",this::showManualDate);
    }
    private void saveManualMatch(ManualMatchEntry draft){
        if(manualMatch!=draft)return;
        try{
            OpponentDirectory.Entry person=opponents.get(draft.opponentId());if(!draft.opponentId().isEmpty()&&(person==null||person.archived))throw new IllegalArgumentException("Choose an available saved opponent or No name");
            MatchBook next=MatchBook.decode(book.encode());OpponentDirectory names=OpponentDirectory.decode(opponents.encode());MatchResult saved=next.importResult(draft.result(UUID.randomUUID().toString()));names.assignTeam(saved.id,draft.opponentId(),draft.partnerId,draft.otherId);
            if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).commit())throw new IllegalStateException("The watch did not confirm saving. Your current match is kept.");
            book=next;opponents=names;manualMatch=null;counter.match(book.current());updateDisplay();showResult(saved.id,resultsPage);
        }catch(Exception invalid){message("Match not saved",safe(invalid),this::showManualMatch);}
    }
    private void showEndMatch(){
        if(book.current().complete()){showMatchControls();return;}
        LinearLayout content=panel("Retirement result");paragraph(content,"Who is quitting? The other player wins by retirement. Scores already played are kept; no extra sets or points are awarded.");
        button(content,book.current().format().doubles?"Our team retires":"I quit",()->confirmRetirement(0));button(content,book.current().format().doubles?"Opposing team retires":"Opponent quit",()->confirmRetirement(1));backButton(content,"Cancel",this::showMatchOptions);
    }
    private void showCancelMatch(){
        if(book.current().complete()){showMatchControls();return;}
        final String before=book.encode(),namesBefore=opponents.encode();
        LinearLayout content=panel("Cancel this match?");
        paragraph(content,book.current().format().name()+" · "+book.current().format().mode()+"\n"+currentGamesText());
        paragraph(content,"Discard this match and all its scores? No result, win/loss or statistics entry will be saved. Your saved history and player names are kept.");
        deleteButton(content,"Discard match",()->{
            if(!before.equals(book.encode())||!namesBefore.equals(opponents.encode())){message("Match changed","Review the match before cancelling it.",this::showMatchControls);return;}
            try{
                MatchBook next=MatchBook.decode(before);OpponentDirectory names=OpponentDirectory.decode(namesBefore);String old=next.current().id();
                next.discardCurrent(UUID.randomUUID().toString());names.forgetMatch(old);
                if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).remove("match_ready_id").putBoolean("match_paused",false).commit())throw new IllegalStateException("The watch did not confirm saving. Your match is kept.");
                book=next;opponents=names;manualMatch=null;scoreMenu=false;counter.match(book.current());updateDisplay();showMenu();
            }catch(Exception error){message("Match not cancelled",safe(error),this::showMatchControls);}
        });
        backButton(content,"Keep playing",this::showMatchControls);
    }
    private void confirmRetirement(int player){
        if(book.current().complete()){showMatchControls();return;}
        final String before=book.encode();LinearLayout content=panel("Save retirement?");
        paragraph(content,(player==0?"You quit. "+opponentName(book.current().id())+" wins.":opponentName(book.current().id())+" quits. You win.")+"\n\n"+currentGamesText()+"\n\nSave this result as retired? You can undo the retirement from the saved result.");
        button(content,"Save retirement",()->{
            if(!before.equals(book.encode())){message("Match changed","Review the current match before ending it.",this::showEndMatch);return;}
            if(book.retire(player)&&afterMatchChange()){
                if(book.result(book.current().id())!=null)showResult(book.current().id(),0);else if(!book.currentDeleted())showSavePending();else showMatchControls();
            }
        });backButton(content,"Cancel",this::showEndMatch);
    }
    private void showNewMatch(){
        if(panel==null)selectSport(book.current().format().sport);
        showNewMatch(this::closePanel);
    }
    private void showNewMatch(Runnable back){
        if(!allowSport(selectedSport))return;
        scrollPositions.remove("new-match-setup");
        newMatchReturn=back;try{newMatchFormat=MatchFormat.decode(preferences.getString("last_format_"+selectedSport,MatchFormat.defaults(selectedSport).encode()));if(!newMatchFormat.sport.equals(selectedSport))newMatchFormat=MatchFormat.defaults(selectedSport);}catch(Exception invalid){newMatchFormat=MatchFormat.defaults(selectedSport);}
        newMatchBestOf=preferences.getInt("last_best_"+selectedSport,selectedSport.equals("squash")?preferences.getInt("last_best_of",book.current().format().sport.equals("squash")?book.current().bestOf():5):3);try{newMatchFormat.validateBest(newMatchBestOf);}catch(Exception invalid){newMatchBestOf=3;}
        newMatchOpponent=newMatchPartner=newMatchOther="";newFirstServer=newFirstReceiver=-1;showNewMatchOptions();
    }
    private void showNewMatchOptions(){
        OpponentDirectory.Entry chosen=opponents.get(newMatchOpponent);if(chosen==null||chosen.archived)newMatchOpponent="";
        LinearLayout content=panel("New match","new-match-setup");section(content,"Scoring & format");
        formatControls(content,newMatchFormat,f->{newMatchFormat=f;newFirstServer=newFirstReceiver=-1;if(!f.doubles){newMatchPartner=newMatchOther="";}showNewMatchOptions();},this::showNewMatchOptions);
        navigationButton(content,"Match length: Best of "+newMatchBestOf,()->chooseLength(newMatchFormat,newMatchBestOf,value->{newMatchBestOf=value;showNewMatchOptions();},this::showNewMatchOptions));
        paragraph(content,newMatchFormat.description());
        section(content,"Players · optional");
        personButton(content,"Opponent: "+(newMatchOpponent.isEmpty()?"No name":chosen.name)+" (optional)",()->showOpponentPicker(null,false,0));
        if(newMatchFormat.doubles){personButton(content,"Partner: "+playerName(newMatchPartner),()->pickPlayer("Partner",id->{newMatchPartner=id;showNewMatchOptions();},this::showNewMatchOptions,0));personButton(content,"Opponent 2: "+playerName(newMatchOther),()->pickPlayer("Opponent 2",id->{newMatchOther=id;showNewMatchOptions();},this::showNewMatchOptions,0));}
        section(content,"Serving · optional");
        navigationButton(content,"First server: "+slotName(newFirstServer,null),()->chooseServer(newMatchFormat,newFirstServer,slot->{newFirstServer=slot;newFirstReceiver=-1;showNewMatchOptions();},this::showNewMatchOptions));
        if(newMatchFormat.doubles&&newFirstServer>=0)navigationButton(content,(newMatchFormat.nested()?"Next opposing server: ":"First receiver: ")+slotName(newFirstReceiver,null),()->chooseReceiver(newMatchFormat,newFirstServer,slot->{newFirstReceiver=slot;showNewMatchOptions();},this::showNewMatchOptions));
        button(content,"Create match",()->confirmNewMatch(newMatchBestOf));
        paragraph(content,"Tap the first point to start play and timing.");backButton(content,"Cancel",newMatchReturn);
    }
    private void confirmNewMatch(int bestOf){
        if(!allowSport(newMatchFormat.sport))return;
        final String selected=newMatchOpponent;
        if((hasCurrentMatch()||book.current().hasPoints())&&(!book.current().complete()||book.result(book.current().id())==null&&!book.currentDeleted())){
            LinearLayout confirm=panel("Replace this match?");paragraph(confirm,"The current match will be cleared. Saved results stay in history.");
            OpponentDirectory.Entry entry=opponents.get(selected);paragraph(confirm,"Opponent: "+(entry==null?"No name":entry.name));
            deleteButton(confirm,"Replace & create match",()->startMatch(bestOf,selected));backButton(confirm,"Back to setup",this::showNewMatchOptions);
        }else startMatch(bestOf,selected);
    }
    private void startMatch(int bestOf,String opponentId){
        if(!allowSport(newMatchFormat.sport))return;
        MatchBook next;OpponentDirectory names;
        try{
            OpponentDirectory.Entry selected=opponents.get(opponentId);if(!opponentId.isEmpty()&&(selected==null||selected.archived))throw new IllegalArgumentException("Choose a saved opponent or No name");
            next=MatchBook.decode(book.encode());names=OpponentDirectory.decode(opponents.encode());next.newMatch(UUID.randomUUID().toString(),bestOf,newMatchFormat);names.assignTeam(next.current().id(),opponentId,newMatchPartner,newMatchOther);next.current().serving(newFirstServer,newFirstReceiver);
        }catch(Exception error){message("Match not started",safe(error),this::showNewMatchOptions);return;}
        if(!preferences.edit().putString("match_book",next.encode()).putString("opponent_book",names.encode()).putString("match_ready_id",next.current().id()).putInt("last_best_of",bestOf).putInt("last_best_"+newMatchFormat.sport,bestOf).putString("last_format_"+newMatchFormat.sport,newMatchFormat.encode()).remove(MatchDisplayPolicy.ACTIVE_MATCH).putBoolean("match_paused",false).commit()){
            message("Match not created","The watch did not confirm saving. Your previous match is kept.",this::showNewMatchOptions);return;
        }
        book=next;opponents=names;counter.match(book.current());updateDisplay();closePanel();
    }
    private String currentGamesText(){
        MatchState match=book.current();StringBuilder text=new StringBuilder();
        for(int i=0;i<match.gameCount();i++){
            if(i>0)text.append('\n');ScoreState game=match.game(i);
            text.append("Set ").append(i+1).append(": ").append(game.blue()).append("–").append(game.red());
            if(game.entered())text.append(" · entered score");
            if(game.winner()==-1)text.append(match.retired()?" · unfinished":" · playing");
        }
        return text.toString();
    }
    private void showCurrentMatch(){
        MatchState match=book.current();LinearLayout content=panel("Match details");
        paragraph(content,"Best of "+match.bestOf()+"\nMe "+match.gamesWon(0)+"–"+match.gamesWon(1)+" "+opponentName(match.id())+"\n\n"+currentGamesText()+"\n\nStarted\n"+book.currentStartedText());
        if(match.retired())paragraph(content,match.retiredPlayer()==0?"Me retired · Opponent won":"Opponent retired · You won");
        if(match.nextReady())button(content,"Next set",()->{book.nextGame();if(afterMatchChange())closePanel();});
        MatchResult result=book.result(match.id());if(result!=null)navigationButton(content,"View result",()->showResult(result.id,resultsPage,this::showCurrentMatch,"Back to Match details"));
        if(match.complete()&&result==null&&!book.currentDeleted())button(content,"Save match result",()->{if(book.saveCompleted(LocalDate.now())){if(afterMatchChange())showResult(match.id(),0);}else showSavePending();});
        if(match.complete()&&book.currentDeleted())paragraph(content,"This result was deleted.");
        backButton(content,"Back to Current match",this::showMatchControls);
    }
    private void showSavePending(){
        LinearLayout content=panel("Result not saved yet");paragraph(content,"Result storage is full. Your completed match is kept on the scoreboard. Delete old results, then open Current match and Save match result.");
        navigationButton(content,"Results",()->openResults(this::showMenu,"Back to Menu"));backButton(content,"Back to Current match",this::showMatchControls);
    }
    private void openResults(Runnable back,String label){resultsReturn=back;resultsReturnLabel=label;showResults(0);}
    private void showResults(int requestedPage){
        List<MatchResult> items=filteredResults();int page=Math.max(0,Math.min(requestedPage,Math.max(0,(items.size()-1)/10)));
        resultsPage=page;LinearLayout content=panel("Results","results:"+selectedSport+":"+filterMode+":"+page+":"+filterOpponent+":"+filterFrom+":"+filterTo);
        LinearLayout tools=ManualEntryUi.row(content);Button filters=navigationButton(tools,"Filters",this::showResultFilters),options=navigationButton(tools,"Options",this::showResultOptions),add=navigationButton(tools,"Add past match",this::showAddPastMatch);filters.setContentDescription("Filter results");options.setContentDescription("Result options");
        for(Button tool:new Button[]{filters,options,add}){tool.setCompoundDrawables(null,null,null,null);tool.setGravity(Gravity.CENTER);tool.setTextSize(11);tool.setPadding(dp(2),dp(4),dp(2),dp(4));tool.setMinWidth(0);tool.setMinimumWidth(0);LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);slot.bottomMargin=dp(4);tool.setLayoutParams(slot);}
        if(items.isEmpty())paragraph(content,book.count()==0?"No saved matches yet. Finished matches are saved here automatically.":"No matches fit these filters.");
        else{
            paragraph(content,items.size()+" of "+book.count()+" saved\nPage "+(page+1)+" of "+Math.max(1,(items.size()+9)/10));
            for(int i=page*10;i<Math.min(items.size(),page*10+10);i++){
                MatchResult result=items.get(i);resultCard(content,result,()->showResult(result.id,page));
            }
            if(page>0)navigationButton(content,"Newer results",()->showResults(page-1));
            if((page+1)*10<items.size())navigationButton(content,"Older results",()->showResults(page+1));
        }
        backButton(content,resultsReturnLabel,resultsReturn);
    }
    private void showResultOptions(){
        LinearLayout content=panel("Result options");
        navigationButton(content,"View results on phone",()->openPhone("results",false,()->showResults(resultsPage),"Back to Results"));
        navigationButton(content,"Export CSV",this::showCsvExport);
        navigationButton(content,"Import CSV",()->openPhone("import",false,this::showResultOptions,"Back to Result options"));
        if(!sportResults(-1).isEmpty())deleteButton(content,"Delete all results",()->confirmDeleteAll(resultsPage));
        backButton(content,"Back to Results",()->showResults(resultsPage));
    }
    private void showCsvExport(){
        LinearLayout content=panel("Export CSV");
        paragraph(content,"Scan a phone QR, then download the CSV in your browser.");
        navigationButton(content,"All results ("+book.count()+")",()->openPhone("results",false,this::showCsvExport,"Back to Export CSV"));
        Button selected=navigationButton(content,"Filtered results ("+filteredResults().size()+")",()->openPhone("results",true,this::showCsvExport,"Back to Export CSV"));selected.setEnabled(!filteredResults().isEmpty());
        backButton(content,"Back to Result options",this::showResultOptions);
    }
    private void showStatistics(){
        MatchStatistics totals=MatchStatistics.from(sportResults(filterMode));
        LinearLayout content=panel("Statistics");
        paragraph(content,modeLabel()+". Date/player filters do not apply.");if(!selectedSport.equals("squash")&&!selectedSport.equals("padel"))navigationButton(content,"Mode: "+modeLabel(),()->chooseResultMode(this::showStatistics));
        if(totals.matchesPlayed()==0)paragraph(content,"No saved matches yet. Finish a match or import results to see your balance.");
        statisticsCard(content,"Matches","Played: "+totals.matchesPlayed()+"\nWon: "+totals.matchesWon+" · Lost: "+totals.matchesLost+"\nBalance: "+signed(totals.matchBalance())+"\nWin rate: "+(totals.matchesPlayed()==0?"—":Math.round(totals.matchWinRate())+"%"));
        statisticsCard(content,MatchFormat.defaults(selectedSport).nested()?"Sets":"Games","Played: "+totals.setsPlayed()+"\nWon: "+totals.setsWon+" · Lost: "+totals.setsLost+"\nBalance: "+signed(totals.setBalance()));
        statisticsCard(content,"Points","Played: "+totals.pointsPlayed()+"\nFor: "+totals.pointsFor+" · Against: "+totals.pointsAgainst+"\nBalance: "+signed(totals.pointBalance()));
        if(MatchFormat.defaults(selectedSport).nested())statisticsCard(content,"Games",totals.gamesFor+" won · "+totals.gamesAgainst+" lost");if(totals.unknownPointMatches>0)paragraph(content,"Point totals exclude unknown points in "+totals.unknownPointMatches+" manually entered matches.");
        navigationButton(content,"Results ("+sportResults(filterMode).size()+")",()->openResults(this::showStatistics,"Back to Statistics"));
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
        paragraph(content,result.format.name()+" · "+result.format.description());if(result.format.doubles)personButton(content,"Players",()->showTeam(id,result.format,again));else personButton(content,"Opponent: "+opponentName(id),()->showOpponentPicker(id,false,0,again));
        resultCard(content,result,null);paragraph(content,"Set scores: Me–Opponent");
        if(id.equals(book.current().id()))button(content,result.retired()?"Undo retirement":book.current().undoEnteredSet()?"Undo entered set":"Undo last point",()->undoPoint(again));
        if(id.equals(book.current().id()))button(content,"New match",()->showNewMatch(again));
        deleteButton(content,"Delete this result",()->confirmDelete(id,page,again));
        backButton(content,backLabel,back);
    }
    private void confirmDelete(String id,int page,Runnable back){
        MatchResult result=book.result(id);if(result==null){showResults(page);return;}
        LinearLayout content=panel("Delete result?");paragraph(content,result.title()+"\n"+result.summary()+"\n\nThis cannot be undone. Other results and your active match are kept.");
        deleteButton(content,"Delete result",()->{
            String before=book.encode();book.delete(id);
            if(persistScore())showResults(page);else{book=MatchBook.decode(before);showDeleteFailure(page);}
        });backButton(content,"Cancel",back);
    }
    private void confirmDeleteAll(int page){
        LinearLayout content=panel("Delete all results?");paragraph(content,"Delete "+sportResults(-1).size()+" "+MatchFormat.name(selectedSport)+" saved results? This cannot be undone. Your active scoreboard is kept, and match numbers will not be reused.");
        deleteButton(content,"Delete all",()->{
            String before=book.encode();for(MatchResult result:sportResults(-1))book.delete(result.id);
            if(persistScore())showResults(0);else{book=MatchBook.decode(before);showDeleteFailure(page);}
        });backButton(content,"Cancel",this::showResultOptions);
    }
    private void showDeleteFailure(int page){counter.match(book.current());LinearLayout content=panel("Delete was not saved");paragraph(content,"The watch could not save the deletion. Your results have been kept. Free storage and retry.");backButton(content,"Back to results",()->showResults(page));}
    private void showSports(){
        sharedArea=true;scoreMenu=false;
        LinearLayout content=panel("Choose sport");
        if(hasCurrentMatch()&&!book.current().complete())button(content,"Resume "+book.current().format().name(),this::showMatchControls);
        if(sportLocked())paragraph(content,"Finish or cancel your "+book.current().format().name()+" match to change sport. Paused matches stay locked.");
        for(String sport:MatchFormat.SPORTS){Button choice=navigationButton(content,MatchFormat.name(sport),()->{if(selectSport(sport))showMenu();});sportButton(choice,sport);boolean enabled=!sportLocked()||sport.equals(book.current().format().sport);choice.setEnabled(enabled);choice.setAlpha(enabled?1f:.4f);choice.setContentDescription(MatchFormat.name(sport)+(enabled?", choose sport":", locked until the current match is finished or cancelled"));}
        section(content,"App · all sports");
        personButton(content,"Players",()->openPlayers(this::showSports,"Back to sports"));settingsButton(content,"Settings",()->openSettings(this::showSports,"Back to sports"));
        backButton(content,"Close app",this::finish);
    }
    private boolean selectSport(String sport){if(!allowSport(sport))return false;if(!selectedSport.equals(sport)){filterOpponent=null;filterFrom=filterTo=null;filterMode=-1;resultsPage=0;}selectedSport=sport;preferences.edit().putString("selected_sport",sport).apply();return true;}
    private void showSportRules(){
        LinearLayout content=panel(MatchFormat.name(selectedSport)+" rules");
        String rules;
        switch(selectedSport){
            case "squash":rules="One point per rally. Games to 11, win by two, without a point cap. A match is normally best of 5 games; best of 3 is also permitted.\n\nThe rally winner serves next. A let replays the rally without a score change. A stroke or service fault awards a point to the appropriate player: tap that player's score.\n\nThe indicator shows who serves, not the service box. At hand-out the server chooses a box; while retaining service the boxes alternate. A let retains the same box.\n\nGame Ball / Match Ball is highlighted. Pause is available for interruptions; its duration is included in elapsed match time.\n\nThis is a scorekeeper. Injury allowances, conduct game/match awards and other referee decisions are not automated. Awarded/conceded games cannot be represented by inventing an 11-point score.\n\nWorld Squash singles rules, effective September 2025.";break;
            case "badminton":rules="Singles or doubles. Best of 3 games. Choose 21-point games (win by two, cap 30) or 15-point games (win by two, cap 21).\n\nThe 15-point format applies internationally from 4 January 2027; specified Austrian competitions switch on 1 January 2027. Local competitions may differ. Both presets remain available.\n\nThe rally winner serves. For doubles, choose the initial server and receiver for each game to show the individual server. Otherwise the indicator can show the serving team.\n\nBWF Laws of Badminton 2025 / 2027.";break;
            case "table-tennis":rules="Singles or doubles. Games to 11, win by two, no point cap. Choose best of 3, 5 or 7.\n\nService changes after two points, and after every point from 10–10. In doubles, the previous receiver becomes server; the previous server's partner receives. Receiving order changes at five points in the deciding game.\n\nChoose the initial server and, for doubles, receiver. A let has no score change. The tournament expedite system is not automated.\n\nITTF Laws of Table Tennis 2026.";break;
            case "padel":rules="Doubles. Points: 0, 15, 30, 40. Sets to 6 games, win by two, tie-break at 6–6 to 7 points, win by two. Best of 3 sets.\n\nChoose Advantage, Golden Point (a deciding point at deuce), or Star Point (two advantage cycles, then a deciding point at the third deuce). Star Point entered FIP circuits in 2026.\n\nOptionally replace the deciding set with a match tie-break to 10, win by two. Choose the first server and next opposing server for service rotation; the pair may choose their order at a new set.\n\nFIP Rules of Padel 2026.";break;
            default:rules="Singles or doubles. Points: 0, 15, 30, 40, advantage. Choose standard advantage or No-Ad (a deciding point at deuce).\n\nSets to 6 games, win by two, tie-break at 6–6 to 7, win by two. Best of 3 or 5 sets. Optionally replace the deciding set with a match tie-break to 10, win by two.\n\nService rotates after each game. In a tie-break the first server serves one point, then servers alternate for two points each. For doubles, choose the first and next opposing server; the pair may choose their order at a new set.\n\nOnly point winners are recorded, without first/second serve attempts.\n\nITF Rules of Tennis 2026.";
        }
        paragraph(content,rules);backButton(content,"Back to Menu",this::showMenu);
    }
    private void formatControls(LinearLayout content,MatchFormat f,java.util.function.Consumer<MatchFormat> apply,Runnable back){
        if(!f.sport.equals("squash")&&!f.sport.equals("padel"))navigationButton(content,"Mode: "+f.mode(),()->selectOption("Match mode","Choose the number of players.",new String[]{"Singles","Doubles"},f.doubles?1:0,value->apply.accept(f.mode(value==1)),back));
        if(f.sport.equals("badminton"))navigationButton(content,"Rules: "+f.target+" points · cap "+f.cap(),()->selectOption("Scoring system","Both rule sets stay available. Choose the format used by your competition.",new String[]{"21 points · cap 30","15 points · cap 21"},f.target==21?0:1,value->apply.accept(f.target(value==0?21:15)),back));
        if(f.nested()){
            String[] labels=f.sport.equals("padel")?new String[]{"Advantage","Golden Point","Star Point"}:new String[]{"Advantage","No-Ad"};
            int selected=f.deuce.equals("advantage")?0:f.deuce.equals("no-ad")?1:2;
            navigationButton(content,"Deuce: "+labels[selected],()->selectOption("Deuce scoring","Advantage: win by two. No-Ad / Golden Point: one deciding point at deuce."+(f.sport.equals("padel")?" Star Point: two advantage cycles, then a deciding point at the third deuce.":""),labels,selected,value->apply.accept(f.deuce(value==0?"advantage":value==1?"no-ad":"star")),back));
            navigationButton(content,"Decider: "+(f.finalTie?"Match tie-break to 10":"Full set"),()->selectOption("Deciding set","Choose how to finish when both sides reach the final set.",new String[]{"Full set","Match tie-break to 10"},f.finalTie?1:0,value->apply.accept(f.finalTie(value==1)),back));
        }
    }
    private void selectOption(String title,String help,String[] labels,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel(title);
        for(int i=0;i<labels.length;i++){final int value=i;choiceButton(content,labels[i],i==selected,()->{if(value==selected)back.run();else apply.accept(value);});}
        paragraph(content,help);
        backButton(content,"Cancel",back);
    }
    private void chooseLength(MatchFormat format,int current,java.util.function.IntConsumer apply,Runnable back){
        int[] values=format.bestOptions();String[] labels=new String[values.length];int selected=-1;
        for(int i=0;i<values.length;i++){labels[i]="Best of "+values[i]+" · first to "+(values[i]/2+1);if(values[i]==current)selected=i;}
        selectOption("Match length","Choose how many "+(format.nested()?"sets":"games")+" decide the match.",labels,selected,index->apply.accept(values[index]),back);
    }
    private void chooseResultMode(Runnable back){selectOption("Filter match mode","Show saved results for the selected sport.",new String[]{"All modes","Singles","Doubles"},filterMode+1,value->{filterMode=value-1;resultsPage=0;back.run();},back);}
    private String playerName(String id){OpponentDirectory.Entry entry=opponents.get(id);return entry==null?"No name":entry.name;}
    private String teamName(String id,int side){
        String a=side==0?"Me":opponents.roleName(id,0),b=opponents.roleName(id,side==0?1:2);
        return (a.isEmpty()?"Opponent 1":a)+" + "+(b.isEmpty()?side==0?"Partner":"Opponent 2":b);
    }
    private String slotName(int slot,String matchId){
        if(slot<0)return "Not set";if(slot==4)return "Our team";if(slot==5)return "Opposing team";if(slot==0)return "Me";
        String name=matchId==null?playerName(slot==1?newMatchOpponent:slot==2?newMatchPartner:newMatchOther):opponents.roleName(matchId,slot==1?0:slot==2?1:2);
        return name.isEmpty()||name.equals("No name")?(slot==1?"Opponent 1":slot==2?"Partner":"Opponent 2"):name;
    }
    private void chooseServer(MatchFormat f,int selected,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel("First server");paragraph(content,f.nested()&&f.doubles?"Choose the first server for this set. Set the next opposing server in the following option.":"Choose the initial server for this game. The indicator follows recorded points; Undo restores it.");
        navigationButton(content,"Not set",()->apply.accept(-1));for(int slot=0;slot<(f.doubles?4:2);slot++){final int choice=slot;personButton(content,(choice==selected?"✓ ":"")+(choice==0?"Me":choice==1?"Opponent 1":choice==2?"Partner":"Opponent 2"),()->apply.accept(choice));}backButton(content,"Cancel",back);
    }
    private void chooseReceiver(MatchFormat f,int server,java.util.function.IntConsumer apply,Runnable back){
        LinearLayout content=panel(f.nested()?"Next opposing server":"First receiver");
        for(int slot=0;slot<4;slot++)if(server<0||slot%2!=server%2){final int choice=slot;personButton(content,choice==0?"Me":choice==1?"Opponent 1":choice==2?"Partner":"Opponent 2",()->apply.accept(choice));}backButton(content,"Cancel",back);
    }
    private void showServing(){
        MatchState match=book.current();int index=match.gameCount()-1;LinearLayout content=panel("Serving indicator","serving:"+match.id());
        paragraph(content,"Next serve: "+slotName(match.server(),match.id())+"\nInitial choices apply to "+match.format().unit().toLowerCase()+" "+(index+1)+". Correcting them preserves scores.");
        navigationButton(content,"First server: "+slotName(match.firstServer(index),match.id()),()->chooseServer(match.format(),match.firstServer(index),slot->{match.serving(slot,-1);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        if(match.format().doubles&&match.firstServer(index)>=0)navigationButton(content,(match.format().nested()?"Next opposing server: ":"First receiver: ")+slotName(match.firstReceiver(index),match.id()),()->chooseReceiver(match.format(),match.firstServer(index),slot->{match.serving(match.firstServer(index),slot);if(persistScore()){counter.refresh();showServing();}},this::showServing));
        backButton(content,"Back to Match options",this::showMatchOptions);
    }
    private void pickPlayer(String title,java.util.function.Consumer<String> choose,Runnable back,int requestedPage){
        List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));LinearLayout content=panel(title);
        personButton(content,"No name",()->choose.accept(""));for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry entry=entries.get(i);playerButton(content,entry,()->choose.accept(entry.id));}
        if(page>0)navigationButton(content,"Previous names",()->pickPlayer(title,choose,back,page-1));if((page+1)*10<entries.size())navigationButton(content,"More names",()->pickPlayer(title,choose,back,page+1));
        personButton(content,"Add player",()->showNameEditor(null,null,back,choose));backButton(content,"Cancel",back);
    }
    private void showTeam(String id,MatchFormat format,Runnable back){
        LinearLayout content=panel("Players");paragraph(content,"Our team: "+teamName(id,0)+"\nOpposing team: "+teamName(id,1));
        for(int slot=0;slot<3;slot++){final int role=slot;personButton(content,(slot==0?"Opponent 1":slot==1?"Partner":"Opponent 2")+": "+playerName(opponents.roleId(id,slot)),()->pickPlayer("Choose player",chosen->{String before=opponents.encode();try{String[] team={opponents.roleId(id,0),opponents.roleId(id,1),opponents.roleId(id,2)};team[role]=chosen;opponents.assignTeam(id,team[0],team[1],team[2]);if(!persistScore())throw new IllegalStateException("Save failed");updateDisplay();showTeam(id,format,back);}catch(Exception error){opponents=OpponentDirectory.decode(before);message("Player not saved",safe(error),()->showTeam(id,format,back));}},()->showTeam(id,format,back),0));}
        backButton(content,"Back",back);
    }
    private String sportsText(String id){java.util.Set<String> sports=opponents.sportsFor(id,book.newestFirst());java.util.List<String> names=new java.util.ArrayList<>();for(String sport:MatchFormat.SPORTS)if(sports.contains(sport))names.add(MatchFormat.name(sport));return names.isEmpty()?"No saved results yet":"Results: "+android.text.TextUtils.join(", ",names);}
    private Button playerButton(LinearLayout content,OpponentDirectory.Entry entry,Runnable action){
        java.util.Set<String> sports=opponents.sportsFor(entry.id,book.newestFirst());StringBuilder label=new StringBuilder(entry.name);if(!sports.isEmpty()){label.append('\n');for(String sport:MatchFormat.SPORTS)if(sports.contains(sport))label.append("◦ ");}
        android.text.SpannableString text=new android.text.SpannableString(label);int start=entry.name.length()+1;for(String sport:MatchFormat.SPORTS)if(sports.contains(sport)){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));text.setSpan(new android.text.style.ImageSpan(icon,android.text.style.ImageSpan.ALIGN_BASELINE),start,start+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);start+=2;}
        Button button=personButton(content,entry.name,action);button.setText(text);button.setContentDescription(entry.name+". "+sportsText(entry.id));return button;
    }
    private void choosePastFormat(){
        if(selectedSport.equals("squash")){manualMatch=new ManualMatchEntry(LocalDate.now());showManualMatch();return;}
        MatchFormat f;try{f=MatchFormat.decode(preferences.getString("last_format_"+selectedSport,MatchFormat.defaults(selectedSport).encode()));}catch(Exception error){f=MatchFormat.defaults(selectedSport);}showPastFormat(f);
    }
    private void showPastFormat(MatchFormat f){LinearLayout content=panel("Past match rules");paragraph(content,f.name()+" · "+f.description());formatControls(content,f,this::showPastFormat,()->showPastFormat(f));button(content,"Continue",()->{manualMatch=new ManualMatchEntry(LocalDate.now(),f);showManualMatch();});backButton(content,"Cancel",()->showResults(resultsPage));}
    private void chooseGenericScore(String title,String opponent,MatchFormat f,boolean tie,int me,int other,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);paragraph(content,f.name()+" · "+(tie?"Match tie-break to 10":f.description())+"\nChoose the winner, then the losing score.");
        button(content,"Me / our team won",()->genericScoreWheel(title,opponent,f,tie,0,Math.min(me,other),apply,back));styledButton(content,"Opponent / other team won",()->genericScoreWheel(title,opponent,f,tie,1,Math.min(me,other),apply,back),ButtonKind.SCORE_OPPONENT);backButton(content,"Cancel",back);
    }
    private void genericScoreWheel(String title,String opponent,MatchFormat f,boolean tie,int winner,int initial,java.util.function.BiConsumer<Integer,Integer> apply,Runnable back){
        LinearLayout content=manualPanel(title);int max=f.nested()&&!tie?6:Math.min(4999,tie?4999:f.cap()-1);NumberPicker wheel=ManualEntryUi.wheel(content,"Losing score",0,max,Math.min(max,initial));TextView preview=paragraph(content,"");Runnable refresh=()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);preview.setText((winner==0?"Me / our team ":"Opponent / other team ")+high+"–"+low);};wheel.setOnValueChangedListener((picker,a,b)->refresh.run());refresh.run();
        button(content,"Use score",()->{int low=wheel.getValue(),high=ManualMatchEntry.winningScore(f,tie,low);apply.accept(winner==0?high:low,winner==0?low:high);});backButton(content,"Cancel",back);
    }
    private String updateDescription(){
        String text="Installed: v"+BuildConfig.VERSION_NAME+"\n\n"+updateStatus;
        if(latest!=null&&latest.newerThan(BuildConfig.VERSION_CODE))text+="\n\nv"+latest.versionName+"\n"+latest.notes;
        String install=preferences.getString("install_status","");
        if(!install.isEmpty())text+="\n\n"+install;
        return text;
    }
    private String opponentName(String matchId){String value=opponents.nameFor(matchId);return value.isEmpty()?"Opponent":value;}
    private List<MatchResult> filteredResults(){List<MatchResult> out=new java.util.ArrayList<>();for(MatchResult result:ResultFilter.select(book,opponents,filterOpponent,filterFrom,filterTo))if(result.format.sport.equals(selectedSport)&&(filterMode<0||result.format.doubles==(filterMode==1)))out.add(result);return out;}
    private List<MatchResult> sportResults(int mode){List<MatchResult> out=new java.util.ArrayList<>();for(MatchResult result:book.newestFirst())if(result.format.sport.equals(selectedSport)&&(mode<0||result.format.doubles==(mode==1)))out.add(result);return out;}
    private String modeLabel(){return filterMode<0?"All modes":filterMode==0?"Singles":"Doubles";}
    private void message(String title,String text,Runnable back){LinearLayout content=panel(title);paragraph(content,text);backButton(content,"Back",back);}
    private void showOpponents(int requestedPage){
        List<OpponentDirectory.Entry> entries=opponents.list(false);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));
        LinearLayout content=panel("Players","opponents:"+page);
        if(entries.isEmpty())paragraph(content,"No names saved. Names are optional; you can keep playing without one.");
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry entry=entries.get(i);playerButton(content,entry,()->showOpponent(entry.id,page));}
        if(page>0)button(content,"Previous names",()->showOpponents(page-1));if((page+1)*10<entries.size())button(content,"More names",()->showOpponents(page+1));
        button(content,"Add player",()->showNameEditor(null,null,()->showOpponents(page)));
        navigationButton(content,"Edit names on phone",()->openPhone("opponents",false,()->showOpponents(page),"Back to Players"));
        TextView legend=paragraph(content,"Icons show sports in saved results.");legend.setTextSize(11);
        backButton(content,playersReturnLabel,playersReturn);
    }
    private void showOpponent(String id,int page){
        OpponentDirectory.Entry entry=opponents.get(id);if(entry==null){showOpponents(page);return;}LinearLayout content=panel(entry.name);paragraph(content,sportsText(entry.id));
        button(content,"Rename",()->showNameEditor(id,null,()->showOpponent(id,page)));
        deleteButton(content,"Remove from player list",()->{LinearLayout confirm=panel("Remove opponent?");paragraph(confirm,"Remove "+entry.name+" from future selection? Past match results keep this name and remain filterable.");deleteButton(confirm,"Remove name",()->{String before=opponents.encode();opponents.archive(id);if(persistScore())showOpponents(page);else{opponents=OpponentDirectory.decode(before);message("Save failed","The name was not removed.",()->showOpponent(id,page));}});backButton(confirm,"Cancel",()->showOpponent(id,page));});
        backButton(content,"Back to Players",()->showOpponents(page));
    }
    private void showNameEditor(String id,String matchId,Runnable back){
        showNameEditor(id,matchId,back,null);
    }
    private void showNameEditor(String id,String matchId,Runnable back,java.util.function.Consumer<String> afterSave){
        OpponentDirectory.Entry entry=id==null?null:opponents.get(id);LinearLayout content=panel(entry==null?"Add player":"Rename player");
        nameInput=new EditText(this);nameInput.setSingleLine(true);nameInput.setTextColor(Color.WHITE);nameInput.setHintTextColor(0xffa7b6c9);nameInput.setHint("Player name");nameInput.setContentDescription("Player name");nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);if(entry!=null)nameInput.setText(entry.name);content.addView(nameInput,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));final EditText input=nameInput;
        button(content,"Speak name",()->{try{Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Player name");startActivityForResult(voice,81);}catch(Exception absent){input.setError("Speech input is unavailable. Type a name or use Edit names on phone.");}});
        button(content,"Save name",()->{String before=opponents.encode();try{String selected=id;if(selected==null)selected=opponents.add(input.getText().toString());else opponents.rename(selected,input.getText().toString());if(matchId!=null)opponents.assign(matchId,selected);if(!persistScore())throw new IllegalStateException("The watch did not confirm saving");if(afterSave!=null)afterSave.accept(selected);updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);input.setError(safe(error));}});
        paragraph(content,"You can also type names from Opponents → Edit names on phone.");backButton(content,"Cancel",back);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK&&data!=null&&nameInput!=null){java.util.ArrayList<String> names=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(names!=null&&!names.isEmpty())nameInput.setText(names.get(0));}}
    private void showOpponentPicker(String matchId,boolean filtering,int requestedPage){
        showOpponentPicker(matchId,filtering,requestedPage,filtering?this::showResultFilters:matchId==null?this::showNewMatchOptions:this::showMatchOptions);
    }
    private void showOpponentPicker(String matchId,boolean filtering,int requestedPage,Runnable back){
        boolean starting=!filtering&&matchId==null;
        List<OpponentDirectory.Entry> entries=opponents.list(filtering);int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/10)));
        LinearLayout content=panel(filtering?"Filter by opponent":"Choose opponent");
        if(filtering)personButton(content,"All opponents",()->{filterOpponent=null;resultsPage=0;showResultFilters();});
        personButton(content,filtering?"Unnamed":"No name",()->{if(filtering){filterOpponent="";resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent="";showNewMatchOptions();}else assignOpponent(matchId,"",back);});
        for(int i=page*10;i<Math.min(entries.size(),page*10+10);i++){OpponentDirectory.Entry e=entries.get(i);personButton(content,e.name+(e.archived?" (removed)":""),()->{if(filtering){filterOpponent=e.id;resultsPage=0;showResultFilters();}else if(starting){newMatchOpponent=e.id;showNewMatchOptions();}else assignOpponent(matchId,e.id,back);});}
        if(page>0)navigationButton(content,"Previous names",()->showOpponentPicker(matchId,filtering,page-1,back));if((page+1)*10<entries.size())navigationButton(content,"More names",()->showOpponentPicker(matchId,filtering,page+1,back));
        if(starting)button(content,"Add player",()->showNameEditor(null,null,this::showNewMatchOptions,id->newMatchOpponent=id));
        else if(!filtering){button(content,"Add player",()->showNameEditor(null,matchId,back));navigationButton(content,"Edit names on phone",()->openPhone("opponents",false,()->showOpponentPicker(matchId,false,page,back),"Back to Choose opponent"));}
        backButton(content,"Cancel",back);
    }
    private void assignOpponent(String matchId,String id,Runnable back){String before=opponents.encode();try{opponents.assign(matchId,id);if(!persistScore())throw new IllegalStateException("The watch did not confirm saving");updateDisplay();back.run();}catch(Exception error){opponents=OpponentDirectory.decode(before);message("Name not saved",safe(error),back);}}
    private void showResultFilters(){
        LinearLayout content=panel("Result filters");if(!selectedSport.equals("squash")&&!selectedSport.equals("padel"))navigationButton(content,"Mode: "+modeLabel(),()->chooseResultMode(this::showResultFilters));OpponentDirectory.Entry e=filterOpponent==null?null:opponents.get(filterOpponent);
        personButton(content,"Opponent: "+(filterOpponent==null?"All":filterOpponent.isEmpty()?"Unnamed":e==null?"Unknown":e.name),()->showOpponentPicker(null,true,0));
        paragraph(content,"Dates: "+(filterFrom==null?"any":filterFrom.toString())+" to "+(filterTo==null?"any":filterTo.toString()));
        button(content,"Today",()->{filterFrom=LocalDate.now();filterTo=filterFrom;showResults(0);});
        button(content,"Last 7 days",()->{filterTo=LocalDate.now();filterFrom=filterTo.minusDays(6);showResults(0);});
        button(content,"This month",()->{filterTo=LocalDate.now();filterFrom=filterTo.withDayOfMonth(1);showResults(0);});
        button(content,"Custom date range",this::showDateRange);
        button(content,"Clear all filters",()->{filterOpponent=null;filterFrom=null;filterTo=null;filterMode=-1;showResults(0);});
        backButton(content,"Back to Results",()->showResults(resultsPage));
    }
    private void showDateRange(){
        LinearLayout content=panel("Date range");paragraph(content,"Use YYYY-MM-DD. Leave either date empty for no limit.");
        EditText from=new EditText(this),to=new EditText(this);for(EditText input:new EditText[]{from,to}){input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(0xffa7b6c9);input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_DATE);content.addView(input,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));}
        from.setHint("From: YYYY-MM-DD");from.setContentDescription("From date");to.setHint("To: YYYY-MM-DD");to.setContentDescription("To date");if(filterFrom!=null)from.setText(filterFrom.toString());if(filterTo!=null)to.setText(filterTo.toString());
        button(content,"Apply dates",()->{try{LocalDate a=from.getText().toString().trim().isEmpty()?null:LocalDate.parse(from.getText().toString().trim()),b=to.getText().toString().trim().isEmpty()?null:LocalDate.parse(to.getText().toString().trim());if(a!=null&&b!=null&&a.isAfter(b))throw new IllegalArgumentException("From date must not follow To date");filterFrom=a;filterTo=b;showResults(0);}catch(Exception invalid){from.setError("Check both dates: YYYY-MM-DD, From before To");}});backButton(content,"Cancel",this::showResultFilters);
    }
    private void openPhone(String view,boolean filtered,Runnable back,String backLabel){
        if(transferBusy){message("Transfer in progress","Wait for the current request, then open the phone link again.",back);return;}
        transferView=view;transferFiltered=filtered;transferReturn=back;transferReturnLabel=backLabel;beginTransfer(filtered);
    }
    private void beginTransfer(boolean filtered){
        if(transferBusy)return;
        if(transfer!=null&&!transfer.expired()){
            if(filtered||transferSnapshotFiltered&&!transferView.equals("opponents"))message("Transfer already open","End the current transfer first, then export your selected results. This keeps its existing phone changes available until you finish.",this::showTransfer);
            else showTransfer();return;
        }
        final String saved=book.encode(),names=opponents.encode();final List<MatchResult> selected=filtered?new java.util.ArrayList<>(filteredResults()):ResultFilter.select(book,opponents,null,null,null);
        transferBusy=true;LinearLayout content=panel("Preparing phone link");paragraph(content,"Creating an encrypted ten-minute link. Both devices need internet.");backButton(content,transferReturnLabel,transferReturn);
        final Dialog waiting=panel;
        tasks.execute(()->{try{TransferClient.Session created=TransferClient.create(MatchBook.decode(saved),OpponentDirectory.decode(names),selected,selectedSport);onUi(()->transferCreated(created,filtered,waiting));}catch(Exception error){onUi(()->{transferBusy=false;if(panel==waiting)message("Transfer unavailable",safe(error),transferReturn);});}});
    }
    private void transferCreated(TransferClient.Session created,boolean filtered,Dialog waiting){transferBusy=false;transfer=created;transferSnapshotFiltered=filtered;if(panel==waiting)showTransfer();}
    private String phoneTitle(){return transferView.equals("opponents")?"Edit names on phone":transferView.equals("import")?"Import CSV":"Results on phone";}
    private String phoneLink(){return transfer.link()+"&view="+transferView;}
    private void showTransfer(){
        LinearLayout content=panel(phoneTitle());
        if(transfer==null||transfer.expired()){
            paragraph(content,"No active phone link. Create an encrypted link that expires after ten minutes.");
            button(content,"Create phone link",()->{transfer=null;beginTransfer(transferFiltered);});
        }else{
            button(content,"Show phone QR",()->showQr(phoneLink(),"Scan with phone",this::showTransfer,"Back to phone link"));
            String purpose=transferView.equals("opponents")?"Edit saved names in your phone browser.":transferView.equals("import")?"Select a Squash Tap CSV in your phone browser.":"Browse results, statistics or download CSV in your phone browser.";
            paragraph(content,purpose+"\n\nExpires "+java.time.Instant.ofEpochMilli(transfer.expiresAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+". Send edits on the phone, then receive them here.");
            Button receive=navigationButton(content,transferBusy?"Receiving…":"Receive changes",this::receiveTransfer);receive.setEnabled(!transferBusy);
            navigationButton(content,"End transfer",()->{if(transferBusy)return;final TransferClient.Session ending=transfer;transfer=null;tasks.execute(()->{try{TransferClient.end(ending);}catch(Exception ignored){}});showTransfer();});
        }
        backButton(content,transferReturnLabel,transferReturn);
    }
    private void receiveTransfer(){
        if(transferBusy||transfer==null)return;final TransferClient.Session session=transfer;transferBusy=true;showTransfer();final Dialog waiting=panel;
        tasks.execute(()->{try{org.json.JSONObject payload=TransferClient.receive(session);onUi(()->{transferBusy=false;if(panel!=waiting)return;try{previewTransfer(payload,session);}catch(Exception error){message("Cannot apply changes",safe(error),this::showTransfer);}});}catch(Exception error){onUi(()->{transferBusy=false;if(panel==waiting)message("Nothing received",safe(error),this::showTransfer);});}});
    }
    private void previewTransfer(org.json.JSONObject payload,TransferClient.Session session)throws Exception{
        final String before=book.encode(),names=opponents.encode();final MatchCsv.Plan plan=TransferClient.preview(payload,book,opponents);
        LinearLayout content=panel("Review phone changes");paragraph(content,plan.added+" new results\n"+plan.duplicates+" duplicates skipped\n"+plan.conflicts+" conflicts skipped\n"+plan.renumbered+" local references adjusted\n\n"+plan.directory.list(false).size()+" saved opponent names\n\nNames may be added, renamed or removed from selection. Past results and the ongoing score are kept.");
        boolean personChanged=!opponents.opponentId(book.current().id()).equals(plan.directory.opponentId(book.current().id()));
        if(personChanged)paragraph(content,(book.current().complete()?"Last completed match":"Current match")+" opponent change:\n"+opponentName(book.current().id())+" → "+(plan.directory.nameFor(book.current().id()).isEmpty()?"No name":plan.directory.nameFor(book.current().id())));
        else paragraph(content,"Match opponent assignment is unchanged.");
        java.util.ArrayList<String> changedNames=new java.util.ArrayList<>();for(OpponentDirectory.Entry e:plan.directory.list(true)){OpponentDirectory.Entry old=opponents.get(e.id);if(old==null)changedNames.add("Add "+e.name+(e.archived?" (removed)":""));else if(!old.name.equals(e.name)||old.archived!=e.archived)changedNames.add((old.name.equals(e.name)?e.name:old.name+" → "+e.name)+(e.archived?" (removed)":old.archived?" (restored)":""));}
        if(!changedNames.isEmpty())paragraph(content,"Name changes:\n"+android.text.TextUtils.join("\n",changedNames.subList(0,Math.min(10,changedNames.size())))+(changedNames.size()>10?"\n… and "+(changedNames.size()-10)+" more":""));
        if(!plan.conflictIds.isEmpty())paragraph(content,"Conflicting IDs:\n"+android.text.TextUtils.join("\n",plan.conflictIds.subList(0,Math.min(3,plan.conflictIds.size()))));
        button(content,"Apply changes",()->{
            if(!before.equals(book.encode())||!names.equals(opponents.encode())){message("Data changed","Receive again to build a fresh preview.",this::showTransfer);return;}
            if(session.expired()){message("Transfer expired","Start a new transfer. Nothing was changed.",this::showTransfer);return;}
            if(!preferences.edit().putString("match_book",plan.book.encode()).putString("opponent_book",plan.directory.encode()).commit()){message("Save failed","Nothing was confirmed. Keep the app open and retry.",this::showTransfer);return;}
            book=plan.book;opponents=plan.directory;counter.match(book.current());updateDisplay();transfer=null;
            tasks.execute(()->{try{TransferClient.end(session);}catch(Exception ignored){}});
            message("Changes saved",plan.added+" results added. Opponents updated. Duplicate and conflicting results were skipped.",transferReturn);
        });backButton(content,"Cancel",this::showTransfer);
    }
    private void showUpdates(){
        LinearLayout content=panel("App updates");updatePanel=true;updateText=paragraph(content,updateDescription());
        settingsButton(content,checking?"Checking…":"Check for updates",this::checkUpdates);
        if(latest!=null&&latest.newerThan(BuildConfig.VERSION_CODE)&&latest.supports(Build.VERSION.SDK_INT)){
            if(getPackageManager().canRequestPackageInstalls())settingsButton(content,downloading?"Downloading…":"Download & update",this::downloadUpdate);
            else settingsButton(content,"Allow app updates",this::allowUpdates);
        }else if(!getPackageManager().canRequestPackageInstalls())settingsButton(content,"Set up direct updates",this::allowUpdates);
        settingsButton(content,"Phone install help",this::phoneHelp);backButton(content,"Back to Settings",this::showSettings);
    }
    private void checkUpdates(){
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
            }catch(Exception error){onUi(()->{checking=false;setUpdateStatus("Check failed: "+safe(error));if(updatePanel)showUpdates();});}
        });
    }
    private void downloadUpdate(){
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
                onUi(()->{downloading=false;setUpdateStatus("Installation requested. Reopen Squash Tap after it finishes.");if(updatePanel)showUpdates();});
            }catch(Exception error){onUi(()->{downloading=false;setUpdateStatus("Update stopped: "+safe(error));if(updatePanel)showUpdates();});}
        });
    }
    private String safe(Exception error){String message=error.getMessage();return message==null?error.getClass().getSimpleName():message;}
    private void onUi(Runnable work){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())work.run();});}
    private void setUpdateStatus(String value){updateStatus=value;preferences.edit().putString("update_status",value).apply();if(updatePanel&&updateText!=null)updateText.setText(updateDescription());}
    private void allowUpdates(){
        try{
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
            startActivity(settings);
        }catch(Exception absent){showPermissionHelp();}
    }
    private void showPermissionHelp(){
        LinearLayout help=panel("Direct updates");
        paragraph(help,"This watch hides the install permission screen. A paired phone's ADB tool may enable it once. No PC is needed.\n\nCommand:\nappops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow\n\nIf this firmware cannot enable it, use the phone installer fallback.");
        settingsButton(help,"Copy command",()->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Update setup","appops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow"));
        });settingsButton(help,"Phone install help",this::phoneHelp);backButton(help,"Back to App updates",this::showUpdates);
    }
    private void phoneHelp(){
        LinearLayout help=panel("Update by phone");
        paragraph(help,"Open the Squash Tap release page on your Android phone. Download the latest watch APK.\n\nIn Wear Installer 2, connect to this watch, choose Custom APK and install the download.\n\nUse the same Wi-Fi network or your phone's hotspot. Your score is kept when installing an update over the existing app.\n\nRelease address:\n"+BuildConfig.SITE_URL);
        backButton(help,"Back to App updates",this::showUpdates);
    }
    private void showDiagnostics(){
        LinearLayout details=panel("Diagnostics");
        String text=diagnosticText(false);
        paragraph(details,text);settingsButton(details,"Send to phone",this::showDiagnosticTransfer);
        settingsButton(details,"Copy details",()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Squash Tap diagnostics",text)));
        backButton(details,"Back to Settings",this::showSettings);
    }
    private String diagnosticText(boolean compact){
        String trace=preferences.getString("lifecycle_trace","");
        if(compact)trace=AppDiagnostics.tailLines(trace,1800);
        String crash=preferences.getString("last_crash","No recorded Java crash");
        if(compact&&crash.length()>1400)crash=crash.substring(0,1400);
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
        String text="Squash Tap v"+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\n"+java.time.LocalDateTime.now()+"\nAndroid API "+Build.VERSION.SDK_INT
            +"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nDirect install allowed: "+getPackageManager().canRequestPackageInstalls()
            +"\nNotifications enabled: "+getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()
            +"\nAmbient support: "+(ambientObserver!=null)+"; resumed: "+resumed+"; panel open: "+(panel!=null)
            +"\nPage: "+panelPage+"; scoreboard flag: "+((getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)+"; screen interactive: "+power.isInteractive()
            +"\nPower saver: "+power.isPowerSaveMode()+"; device idle: "+power.isDeviceIdleMode()+"; battery exempt: "+power.isIgnoringBatteryOptimizations(getPackageName())
            +"\nBattery (whole watch): "+BatterySnapshot.describe(this)+"; session active: "+sessionOngoing()
            +"\nScore: "+book.current().game().blue()+"–"+book.current().game().red()+"; sets "+book.current().gamesWon(0)+"–"+book.current().gamesWon(1)
            +"\nBest of "+book.current().bestOf()+"; saved results "+book.count()
            +"\nPaused / waiting to resume: "+needsResume()+"; ambient: "+ambient+"; keep awake setting: "+preferences.getBoolean("match_keep_awake_v3",true)
            +"\n\n"+updateDescription()+"\n\n"+preferences.getString("diagnostic","")
            +"\n\nLast crash:\n"+crash+"\n\nProcess exit reasons:\n"+AppDiagnostics.recentExits(this)+"\n\nLifecycle trace:\n"+trace;
        return text;
    }
    private void showDiagnosticTransfer(){
        try{showQr(DiagnosticTransfer.link(BuildConfig.SITE_URL,diagnosticText(true)),"Scan with phone",this::showDiagnostics);}
        catch(Exception error){message("Diagnostic transfer",safe(error),this::showDiagnostics);}
    }
    private void showQr(String link,String title,Runnable back){
        showQr(link,title,back,"Back to Diagnostics");
    }
    private void showQr(String link,String title,Runnable back,String backLabel){
        try{
            java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L);
            hints.put(com.google.zxing.EncodeHintType.MARGIN,2);hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");
            int size=Math.round(Math.min(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels)*.64f);
            com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,com.google.zxing.BarcodeFormat.QR_CODE,size,size,hints);
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.RGB_565);
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            closePanel();panel=new Dialog(this);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
            View qr=new View(this){
                final android.graphics.Paint p=new android.graphics.Paint();
                @Override protected void onDraw(android.graphics.Canvas canvas){
                    canvas.drawColor(Color.BLACK);p.setColor(Color.WHITE);p.setTextAlign(android.graphics.Paint.Align.CENTER);p.setTextSize(getWidth()*.045f);
                    canvas.drawText(title,getWidth()*.5f,getHeight()*.12f,p);
                    canvas.drawBitmap(bitmap,(getWidth()-bitmap.getWidth())/2f,(getHeight()-bitmap.getHeight())/2f,p);
                    p.setColor(0xff76cfff);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1));
                    canvas.drawRoundRect(new android.graphics.RectF(getWidth()*.16f,getHeight()*.845f,getWidth()*.84f,getHeight()*.935f),dp(12),dp(12),p);
                    p.setStyle(android.graphics.Paint.Style.FILL);canvas.drawText(backLabel,getWidth()*.5f,getHeight()*.90f,p);
                }
                @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_UP&&event.getY()>getHeight()*.83f)back.run();return true;}
            };
            panelPage=backLabel.equals("Back to Diagnostics")?"Diagnostic QR":"Phone QR";panel.setContentView(swipeContainer(qr,panel));panel.show();panel.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));panel.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);panel.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);trackPanelDismiss();bindBack(back);updateDisplay();
            final Window qrWindow=panel.getWindow();qr.postDelayed(()->qrWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON),60000L);
        }catch(Exception error){message("QR unavailable",safe(error),back);}
    }
    private LinearLayout panel(String title){
        return panel(title,null);
    }
    private LinearLayout panel(String title,String scrollKey){
        closePanel();
        panel=new Dialog(this);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff080d17);scroll.setClipToPadding(false);
        scroll.setPadding(dp(30),dp(28),dp(30),dp(30));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        if(title.equals("Choose sport")){TextView brand=paragraph(content,"SQUASH TAP");brand.setTextSize(10);brand.setTextColor(0xff9daec4);}
        else sportBadge(content,sharedArea?null:selectedSport);
        LinearLayout header=ManualEntryUi.row(content);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=paragraph(header,title);heading.setTextSize(17);heading.setTextColor(0xffcdeaff);heading.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if(!title.equals("Menu")&&!title.equals("Choose sport")&&!title.endsWith("?")&&book.current().format().sport.equals(selectedSport)&&(sessionOngoing()||needsResume())){
            Button shortcut=styledButton(header,"Scoreboard",this::closePanel,ButtonKind.SHORTCUT);shortcut.setPadding(dp(4),dp(4),dp(4),dp(4));shortcut.setTextSize(11);shortcut.setLayoutParams(new LinearLayout.LayoutParams(dp(78),dp(48)));
        }
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
        panel.setOnDismissListener(d->{if(panel==shown){rememberPanelScroll();updatePanel=false;updateText=null;panel=null;panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";updateDisplay();}});
    }
    private View swipeContainer(View child,Dialog shown){
        SwipeDismissFrameLayout frame=new SwipeDismissFrameLayout(this);frame.setSwipeable(true);frame.addView(child,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addCallback(new SwipeDismissFrameLayout.Callback(){@Override public void onDismissed(SwipeDismissFrameLayout layout){if(panel==shown)shown.cancel();}});return frame;
    }
    private void bindBack(Runnable action){
        final Dialog shown=panel;
        shown.setOnCancelListener(d->{if(!isFinishing()&&!isDestroyed()&&(panel==shown||panel==null))action.run();});
        shown.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())shown.cancel();return true;});
    }
    @Override public void onBackPressed(){if(panel!=null)panel.cancel();else super.onBackPressed();}
    private void rememberPanelScroll(){
        if(panelScrollKey==null||panelScroll==null)return;
        scrollPositions.put(panelScrollKey,panelScroll.getScrollY());
        if(scrollPositions.size()>64)scrollPositions.remove(scrollPositions.keySet().iterator().next());
    }
    private TextView paragraph(LinearLayout content,String text){
        TextView view=new TextView(this);view.setText(text);view.setTextColor(0xffd7e2f0);view.setTextSize(13);
        view.setGravity(Gravity.CENTER);view.setPadding(0,dp(5),0,dp(9));
        content.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return view;
    }
    private void section(LinearLayout content,String text){TextView label=paragraph(content,text.toUpperCase(java.util.Locale.ROOT));label.setTextSize(10);label.setTextColor(0xff9daec4);label.setPadding(0,dp(7),0,dp(3));}
    private void sportBadge(LinearLayout content,String sport){
        TextView badge=paragraph(content,sport==null?"ALL SPORTS":MatchFormat.name(sport));badge.setTextSize(12);badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);badge.setTextColor(sport==null?0xffd9c6ff:SportIcons.accent(sport));badge.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(12));shape.setColor(sport==null?0xff241c32:(SportIcons.accent(sport)&0x00ffffff)|0x18000000);badge.setBackground(shape);
        if(sport!=null){SportIcons icon=new SportIcons(sport,dp(18));icon.setBounds(0,0,dp(18),dp(18));badge.setCompoundDrawables(icon,null,null,null);badge.setCompoundDrawablePadding(dp(5));}
        badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));badge.setContentDescription(sport==null?"Shared across all sports":"Current sport: "+MatchFormat.name(sport));
    }
    private void sportButton(Button button,String sport){
        int accent=SportIcons.accent(sport);GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(15));shape.setColor((accent&0x00ffffff)|0x18000000);shape.setStroke(dp(1),(accent&0x00ffffff)|0x66000000);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff687589,accent}));button.setCompoundDrawablesWithIntrinsicBounds(new SportIcons(sport,dp(22)),null,null,null);button.setCompoundDrawablePadding(dp(8));
    }
    private Button choiceButton(LinearLayout content,String text,boolean selected,Runnable action){
        Button option=styledButton(content,(selected?"✓ ":"")+text,action,sharedArea?ButtonKind.SETTINGS:ButtonKind.NAVIGATION);option.setContentDescription(text+(selected?", selected":", select"));option.setSelected(selected);
        if(selected){GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(15));shape.setColor(sharedArea?0xff363636:0xff123047);shape.setStroke(dp(1),sharedArea?0xffa7b6c9:SportIcons.accent(selectedSport));option.setBackgroundTintList(null);option.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40ffffff),shape,null));}
        return option;
    }
    private Button button(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.MATCH);}
    private Button navigationButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.NAVIGATION);}
    private Button personButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.PERSON);}
    private Button settingsButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.SETTINGS);}
    private Button deleteButton(LinearLayout content,String text,Runnable action){return styledButton(content,text,action,ButtonKind.DELETE);}
    private Button backButton(LinearLayout content,String text,Runnable action){bindBack(action);return styledButton(content,text,action,ButtonKind.BACK);}
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
        if(open!=null){reference=paragraph(card,result.title());reference.setTextSize(11);reference.setTextColor(0xffedf4ff);}
        paragraph(card,result.format.name()+" · "+result.format.mode()+"\n"+(result.format.doubles?teamName(result.id,0)+" vs "+teamName(result.id,1):"vs "+opponentName(result.id)));
        TextView score=paragraph(card,result.format.doubles?"Our team "+result.gamesWon(0)+"–"+result.gamesWon(1)+" Opponents":result.summary().replace("Opponent",opponentName(result.id)));score.setTextSize(17);score.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);score.setTextColor(Color.WHITE);
        TextView details=paragraph(card,"Best of "+result.bestOf+" · "+result.outcomeText()+(open==null?"\n"+result.timingText():"")+"\nDuration: "+result.durationText()+"\nMatch "+result.number+(open==null?"\n\n"+result.gamesText():""));details.setTextSize(12);details.setTextColor(0xffedf4ff);
        if(open!=null){
            card.setFocusable(true);card.setClickable(true);card.setContentDescription(result.title()+". "+result.summary()+". Open match result.");
            reference.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);score.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);details.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.setOnClickListener(v->open.run());
        }
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);position.bottomMargin=dp(12);content.addView(card,position);
    }
    private void closePanel(){rememberPanelScroll();if(panel!=null){panel.dismiss();panel=null;}panelScroll=null;panelScrollKey=null;panelPage="Scoreboard";updatePanel=false;updateText=null;updateDisplay();}
}
