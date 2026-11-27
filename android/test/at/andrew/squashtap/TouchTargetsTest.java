package at.andrew.squashtap;
import org.junit.Test;
import static org.junit.Assert.*;
public class TouchTargetsTest {
    @Test public void leftCardIsBlue(){assertEquals(0,TouchTargets.hit(.25f,.45f));}
    @Test public void rightCardIsRed(){assertEquals(1,TouchTargets.hit(.75f,.45f));}
    @Test public void dividerDoesNotScore(){assertEquals(-1,TouchTargets.hit(.5f,.45f));}
    @Test public void topOpensMenu(){assertEquals(4,TouchTargets.hit(.5f,.15f));}
    @Test public void footerUndo(){assertEquals(2,TouchTargets.hit(.3f,.82f));}
    @Test public void footerNew(){assertEquals(3,TouchTargets.hit(.7f,.82f));}
    @Test public void outsideDoesNothing(){assertEquals(-1,TouchTargets.hit(-.1f,.45f));assertEquals(-1,TouchTargets.hit(.5f,1.1f));}
    @Test public void swipeIsNotTap(){assertFalse(TouchTargets.isTap(20,20,130,20,200,15));}
    @Test public void longPressIsNotTap(){assertFalse(TouchTargets.isTap(20,20,20,20,1200,15));}
    @Test public void normalTapWorks(){assertTrue(TouchTargets.isTap(20,20,22,24,150,15));}
    @Test public void shapesKeepFiveSeparateTargetsAndMakeUseOfRectangleEdges(){
        for(float[] size:new float[][]{{180,180},{192,192},{201,238},{240,240}}){
            for(boolean round:new boolean[]{true,false}){
                float w=size[0],h=size[1];
                float[] menu=TouchTargets.area(TouchTargets.MENU,w,h,round);
                float[] blue=TouchTargets.area(TouchTargets.BLUE,w,h,round);
                float[] undo=TouchTargets.area(TouchTargets.UNDO,w,h,round);
                assertTrue(menu[3]<blue[1]);assertTrue(blue[3]<undo[1]);
                for(int target=TouchTargets.BLUE;target<=TouchTargets.MENU;target++){
                    float[] box=TouchTargets.area(target,w,h,round);
                    assertTrue((box[2]-box[0])*w>=47.99f);assertTrue((box[3]-box[1])*h>=47.99f);
                    assertEquals(target,TouchTargets.hit((box[0]+box[2])/2,(box[1]+box[3])/2,w,h,round));
                }
                assertEquals(TouchTargets.OUTSIDE,TouchTargets.hit(.5f,.5f,w,h,round));
                assertEquals(TouchTargets.OUTSIDE,TouchTargets.hit(.3f,(blue[3]+undo[1])/2,w,h,round));
            }
        }
        assertEquals(TouchTargets.BLUE,TouchTargets.hit(.04f,.5f,201,238,false));
        assertEquals(TouchTargets.OUTSIDE,TouchTargets.hit(.04f,.5f,201,238,true));
    }
}
