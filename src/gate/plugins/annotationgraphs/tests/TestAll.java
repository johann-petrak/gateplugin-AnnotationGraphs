package gate.plugins.annotationgraphs.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  Test1.class,
})
public class TestAll {
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main(TestAll.class.getCanonicalName());
  }  
}
