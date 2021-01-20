package misk.web.extractors;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.io.IOException;
import javax.inject.Inject;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;
import misk.web.Get;
import misk.web.PathParam;
import misk.web.ResponseContentType;
import misk.web.WebActionModule;
import misk.web.WebTestingModule;
import misk.web.actions.WebAction;
import misk.web.jetty.JettyService;
import misk.web.mediatype.MediaTypes;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MiskTest
public class JavaPathParamDispatchTest {
  @MiskTestModule final Module module = new TestModule();

  public enum ResourceType {
    USER,
    FILE,
    FOLDER
  }

  private final OkHttpClient httpClient = new OkHttpClient();
  @Inject private JettyService jettyService;

  @Test
  public void pathParamsConvertToProperTypes() throws IOException {
    okhttp3.Response response = get("/objects/FILE/defaults/245");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).isEqualTo("(type=FILE,name=defaults,version=245)");
  }

  public static final class GetObjectDetails implements WebAction {
    @Inject
    public GetObjectDetails() {
    }

    @Get(pathPattern = "/objects/{resourceType}/{name}/{version}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    public String getObjectDetails(
        @PathParam("resourceType") ResourceType resourceType,
        @PathParam("name") String name,
        @PathParam("version") long version
    ) {
      return "(type=" + resourceType + ",name=" + name + ",version=" + version + ")";
    }
  }

  private static final class TestModule extends AbstractModule {
    @Override protected void configure() {
      install(new WebTestingModule());
      install(WebActionModule.create(GetObjectDetails.class));
    }
  }

  private okhttp3.Response get(String path) throws IOException {
    Request request = new Request.Builder()
        .get()
        .url(jettyService.getHttpServerUrl().newBuilder().encodedPath(path).build())
        .build();
    return httpClient.newCall(request).execute();
  }
}
