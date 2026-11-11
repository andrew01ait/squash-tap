package at.andrew.squashtap;
import androidx.wear.tiles.*;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.concurrent.futures.ResolvableFuture;
import com.google.common.util.concurrent.ListenableFuture;

public final class KeepScoreTileService extends TileService {
    private static <T> ListenableFuture<T> immediate(T value){ResolvableFuture<T> future=ResolvableFuture.create();future.set(value);return future;}
    private LayoutElementBuilders.Text text(String s,float size,int color){return new LayoutElementBuilders.Text.Builder().setText(s).setMaxLines(1).setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(size)).setColor(ColorBuilders.argb(color)).build()).build();}
    @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request){
        MatchWidgets.Data d=MatchWidgets.read(this);float width=Math.max(120,request.getDeviceConfiguration().getScreenWidthDp()*.72f);
        ModifiersBuilders.Clickable click=new ModifiersBuilders.Clickable.Builder().setId("open-match").setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(new ActionBuilders.AndroidActivity.Builder().setPackageName(getPackageName()).setClassName(MainActivity.class.getName()).addKeyToExtraMapping("keep_score_widget",new ActionBuilders.AndroidStringExtra.Builder().setValue(d.action).build()).build()).build()).build();
        LayoutElementBuilders.Box action=new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.dp(width)).setHeight(DimensionBuilders.dp(48)).setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(click).setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xff1668cb)).setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(18)).build()).build()).build()).addContent(text(d.present?"Open match":"Choose sport",13,0xffedf4ff)).build();
        LayoutElementBuilders.Column content=new LayoutElementBuilders.Column.Builder().setWidth(DimensionBuilders.dp(width)).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).addContent(text("KEEP SCORE",10,0xff9daec4)).addContent(text(d.present?d.sport+" · "+d.state:"Ready to play",11,0xffcdeaff)).addContent(text(d.score,34,0xffedf4ff)).addContent(text(d.sets,10,0xffa7b6c9)).addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6)).build()).addContent(action).build();
        LayoutElementBuilders.Box root=new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand()).setModifiers(new ModifiersBuilders.Modifiers.Builder().setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(d.description).build()).setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xff080d17)).build()).build()).addContent(content).build();
        return immediate(new TileBuilders.Tile.Builder().setResourcesVersion("1").setFreshnessIntervalMillis(0).setTileTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder().setLayout(new LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build()).build()).build());
    }
    @Override protected ListenableFuture<androidx.wear.protolayout.ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request){return immediate(new androidx.wear.protolayout.ResourceBuilders.Resources.Builder().setVersion("1").build());}
}
