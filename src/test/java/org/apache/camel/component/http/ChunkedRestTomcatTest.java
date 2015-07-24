package org.apache.camel.component.http;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Test to verify behaviour of chunked data input through Tomcat.
 */
public class ChunkedRestTomcatTest extends CamelTestSupport {

  private static final String EXPECTED_INPUT = "EXPECTED INPUT";

  private static final String SERVLET_NAME = "SERVLET_NAME";

  private Tomcat tc;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @EndpointInject(uri = "mock:result")
  private MockEndpoint result;
  private CloseableHttpClient client;
  private HttpPost post;

  @Override
  protected void doPreSetup() throws Exception {
    super.doPreSetup();
    tc = new Tomcat();
    tc.setPort(PortAcquirer.getUnboundPort());
    Context context = tc.addContext("/", folder.getRoot().getAbsolutePath());
    Tomcat.addServlet(context, SERVLET_NAME, new CamelHttpTransportServlet());
    context.addServletMapping("/*", SERVLET_NAME);
    tc.start();

    client = HttpClients.createDefault();
    post = new HttpPost("http://localhost:" + tc.getConnector().getPort() + "/test");
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    tc.stop();
    client.close();
  }

  @Override
  protected RouteBuilder createRouteBuilder() throws Exception {
    return new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        restConfiguration()
            .component("servlet")
            .endpointProperty("servletName", SERVLET_NAME);

        rest().post("/test")
            .route()
            .to("mock:result")
            .transform().simple("COMPLETE");
      }
    };
  }

  @Test
  public void testChunked() throws Exception {
    /*
     * With an InputStream the http post uses HTTP/1.1 chunked transfer encoding, the tomcat CoyoteInputStream that
     * is retrieved in the HttpMessage returns 0 for .available() which causes this to treat the message as having no
     * body despite the fact that if you read the stream all the data is available.
     */
    try (InputStream inputStream = new ByteArrayInputStream(EXPECTED_INPUT.getBytes("UTF-8"))) {
      sendAndVerifyEntity(new InputStreamEntity(inputStream));
    }
  }

  @Test
  public void testUnchunked() throws Exception {
    sendAndVerifyEntity(new StringEntity(EXPECTED_INPUT));
  }

  private void sendAndVerifyEntity(AbstractHttpEntity entity) throws Exception {
    result.setExpectedCount(1);

    post.setEntity(entity);
    try (CloseableHttpResponse response = client.execute(post)) {
      EntityUtils.consume(response.getEntity());
    }

    result.message(0).body().isEqualTo(EXPECTED_INPUT);
    result.assertIsSatisfied();
  }
}
