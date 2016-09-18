using NUnit.Framework;
using NLog;

namespace Org.SavantBuild.Test
{
  [TestFixture]
  [Category("Unit")]
  public class MyClassTest
  {
    protected static readonly Logger logger = LogManager.GetCurrentClassLogger();

    [Test]
    public void test()
    {
      MyClass mc = new MyClass();
      mc.testable();
    }
  }
}