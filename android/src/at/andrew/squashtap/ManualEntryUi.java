package at.andrew.squashtap;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

/** Touch-only input components: native scroll wheels and the scoreboard's blue/red language. */
final class ManualEntryUi {
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(parent.getContext());r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER);parent.addView(r,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return r;}
    static void equal(View first,View second){for(View v:new View[]{first,second}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.bottomMargin=dp(v.getContext(),8);if(v==first)p.rightMargin=dp(v.getContext(),6);v.setLayoutParams(p);v.setMinimumWidth(0);if(v instanceof TextView)((TextView)v).setMinWidth(0);}}
    static NumberPicker wheel(LinearLayout parent,String label,int min,int max,int value){
        NumberPicker picker=new NumberPicker(parent.getContext());picker.setMinValue(min);picker.setMaxValue(max);picker.setWrapSelectorWheel(false);picker.setValue(value);picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);picker.setContentDescription(label);picker.setTextColor(Color.WHITE);picker.setTextSize(dp(parent.getContext(),26));picker.setSelectionDividerHeight(dp(parent.getContext(),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(parent.getContext(),112),dp(parent.getContext(),116));p.gravity=Gravity.CENTER_HORIZONTAL;parent.addView(picker,p);return picker;
    }
    static ScoreCard score(LinearLayout parent,String title,String opponent,int me,int other,Runnable edit){ScoreCard card=new ScoreCard(parent.getContext(),title,opponent);card.scores(me,other);if(edit!=null){card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->edit.run());}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=dp(parent.getContext(),10);parent.addView(card,p);return card;}
    static final class ScoreCard extends LinearLayout {
        private final TextView meValue,otherValue;private final String title,opponent;
        ScoreCard(Context c,String title,String opponent){super(c);this.title=title;this.opponent=opponent;setOrientation(VERTICAL);
            TextView heading=text(c,title,12);heading.setPadding(0,dp(c,5),0,dp(c,5));addView(heading,new LayoutParams(-1,-2));LinearLayout pair=new LinearLayout(c);pair.setOrientation(HORIZONTAL);addView(pair,new LayoutParams(-1,-2));
            meValue=side(pair,"Me",0xff1668cb);otherValue=side(pair,opponent,0xffb82e4c);
        }
        private TextView side(LinearLayout pair,String name,int colour){Context c=getContext();LinearLayout side=new LinearLayout(c);side.setOrientation(VERTICAL);side.setPadding(dp(c,5),dp(c,8),dp(c,5),dp(c,8));GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(dp(c,13));side.setBackground(shape);LayoutParams p=new LayoutParams(0,-2,1);if(pair.getChildCount()==0)p.rightMargin=dp(c,6);pair.addView(side,p);TextView label=text(c,name,11);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);side.addView(label,new LayoutParams(-1,-2));TextView value=text(c,"0",34);value.setTypeface(Typeface.DEFAULT_BOLD);side.addView(value,new LayoutParams(-1,-2));return value;}
        void scores(int me,int other){meValue.setText(Integer.toString(me));otherValue.setText(Integer.toString(other));meValue.setTextSize(me>=1000?22:me>=100?28:34);otherValue.setTextSize(other>=1000?22:other>=100?28:34);setContentDescription(title+". Me "+me+", "+opponent+" "+other+". Score entry.");}
        private static TextView text(Context c,String text,int size){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setGravity(Gravity.CENTER);return v;}
    }
}
