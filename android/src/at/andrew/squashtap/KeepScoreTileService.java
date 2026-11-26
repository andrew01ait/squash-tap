package at.andrew.squashtap;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import com.google.common.util.concurrent.ListenableFuture;

public final class KeepScoreTileService extends TileService {
    private static final String RESOURCE_VERSION="2";
    private static final String LOGO="keep-score-logo";

    private LayoutElementBuilders.Row brand(){
        LayoutElementBuilders.Image logo=new LayoutElementBuilders.Image.Builder().setResourceId(LOGO)
                .setWidth(DimensionBuilders.dp(20)).setHeight(DimensionBuilders.dp(20)).build();
        return new LayoutElementBuilders.Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(logo).addContent(new LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(3)).build())
                .addContent(text(getString(R.string.app_name),13,0xffffffff)).build();
    }

    private static <T> ListenableFuture<T> immediate(T value){
        ResolvableFuture<T> future=ResolvableFuture.create();
        future.set(value);
        return future;
    }

    private LayoutElementBuilders.Text text(String label,float size,int colour){
        return new LayoutElementBuilders.Text.Builder().setText(label).setMaxLines(1)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(size))
                        .setColor(ColorBuilders.argb(colour)).build()).build();
    }

    @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request){
        WatchLanguage lang=WatchLanguage.get(this);
        MatchWidgets.Data data=MatchWidgets.read(this);
        float width=Math.max(120,request.getDeviceConfiguration().getScreenWidthDp()*.72f);
        ActionBuilders.AndroidActivity activity=new ActionBuilders.AndroidActivity.Builder()
                .setPackageName(getPackageName()).setClassName(MainActivity.class.getName())
                .addKeyToExtraMapping("keep_score_widget",new ActionBuilders.AndroidStringExtra.Builder().setValue(data.action).build()).build();
        ModifiersBuilders.Clickable click=new ModifiersBuilders.Clickable.Builder().setId("open-match")
                .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build()).build();
        MenuSection destination=data.present?MenuSection.CURRENT_MATCH:MenuSection.HOME;
        ModifiersBuilders.Background fill=new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(destination.surface))
                .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(18)).build()).build();
        LayoutElementBuilders.Box action=new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(width)).setHeight(DimensionBuilders.dp(48))
                .setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(click).setBackground(fill).build())
                .addContent(text(lang.text(data.present?"Open match":"Home"),13,0xffffffff)).build();
        LayoutElementBuilders.Column.Builder content=new LayoutElementBuilders.Column.Builder().setWidth(DimensionBuilders.dp(width))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).addContent(brand());
        if(data.present){
            content.addContent(text(data.sport+" · "+data.state,11,0xffcdeaff))
                    .addContent(text(data.score,34,0xffedf4ff)).addContent(text(data.sets,10,0xffa7b6c9));
        }else{
            content.addContent(text(lang.text("Ready to play"),13,0xffcdeaff));
        }
        content.addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6)).build()).addContent(action);
        LayoutElementBuilders.Box root=new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand())
                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                        .setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription(data.description).build())
                        .setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xff080d17)).build()).build())
                .addContent(content.build()).build();
        TimelineBuilders.TimelineEntry entry=new TimelineBuilders.TimelineEntry.Builder()
                .setLayout(new LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build();
        return immediate(new TileBuilders.Tile.Builder().setResourcesVersion(RESOURCE_VERSION).setFreshnessIntervalMillis(0)
                .setTileTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build()).build());
    }

    @Override protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request){
        ResourceBuilders.ImageResource logo=new ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(new ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.ic_launcher).build()).build();
        return immediate(new ResourceBuilders.Resources.Builder().setVersion(RESOURCE_VERSION).addIdToImageMapping(LOGO,logo).build());
    }
}
