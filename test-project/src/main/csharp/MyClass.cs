using System;
using System.Net;
using NLog;

namespace Org.SavantBuild.Test
{
  public class MyClass
  {
    protected static readonly Logger logger = LogManager.GetCurrentClassLogger();

    protected HttpWebRequest request;

    public void testable()
    {
      Console.WriteLine("Hello world");
    }
  }
}