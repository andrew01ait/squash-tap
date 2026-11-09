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
}
