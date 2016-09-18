using NUnit.Framework;
using NLog;

namespace Org.SavantBuild.Test
{
  [TestFixture]
  [Category("Integration")]
  public class MyClassIntegrationTest
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