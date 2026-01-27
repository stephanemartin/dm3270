package com.bytezone.dm3270;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytezone.dm3270.attributes.StartFieldAttribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenContext;
import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.display.ScreenPosition;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.abstracta.wiresham.Flow;
import us.abstracta.wiresham.VirtualTcpService;

@RunWith(MockitoJUnitRunner.class)
public class TerminalClientTest {

  private static final Logger LOG = LoggerFactory.getLogger(TerminalClientTest.class);
  private static final int TERMINAL_MODEL_TYPE_TWO = 2;
  private static final int TERMINAL_MODEL_TYPE_THREE = 3;
  private static final ScreenDimensions SCREEN_DIMENSIONS = new ScreenDimensions(24, 80);
  private static final long TIMEOUT_MILLIS = 10000;
  private static final String SERVICE_HOST = "localhost";
  private static final String LOGIN_SPECIAL_CHARACTERS_FLOW = "/login-special-characters.yml";
  private static final String APP_NAME = "testapp";
  private static final String USERNAME = "testusr";
  private static final String PASSWORD = "testpsw";

  @Rule
  public TestRule watchman = new TestWatcher() {
    @Override
    public void starting(Description description) {
      LOG.debug("Starting {}", description.getMethodName());
    }

    @Override
    public void finished(Description description) {
      LOG.debug("Finished {}", description.getMethodName());
    }
  };
  private final VirtualTcpService service = new VirtualTcpService();
  private TerminalClient client;
  private ExceptionWaiter exceptionWaiter;
  private final ScheduledExecutorService stableTimeoutExecutor = Executors
      .newSingleThreadScheduledExecutor();
  @Mock
  private Screen screenMock;
  @Mock
  private ConnectionListener connectionListenerMock;

  @Before
  public void setup() throws IOException {
    service.setSslEnabled(false);
    startServiceWithFlow("/login.yml");
    client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
    client.setConnectionTimeoutMillis(5000);
    exceptionWaiter = new ExceptionWaiter();
    client.addConnectionListener(exceptionWaiter);
    connectClient();
  }

  private static class ExceptionWaiter implements ConnectionListener {

    private final CountDownLatch exceptionLatch = new CountDownLatch(1);

    private final CountDownLatch closeLatch = new CountDownLatch(1);

    @Override
    public void onConnection() {
    }

    @Override
    public void onException(Exception ex) {
      exceptionLatch.countDown();
    }

    @Override
    public void onConnectionClosed() {
      closeLatch.countDown();
    }

    private void awaitException() throws InterruptedException {
      assertThat(exceptionLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
    }

    private void awaitClose() throws InterruptedException {
      assertThat(closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
    }

  }

  private void connectClient() {
    client.connect(SERVICE_HOST, service.getPort());
    client.addScreenChangeListener(
        screenWatcher -> LOG.debug("Screen updated, cursor={}, alarm={}, screen:{}",
            client.getCursorPosition().orElse(null), client.isAlarmOn(), getScreenText()));
  }

  private String getScreenText() {
    return client.getScreenText().replace('\u0000', ' ');
  }

  private void startServiceWithFlow(String s) throws IOException {
    service.setFlow(Flow.fromYml(new File(getResourceFilePath(s))));
    service.start();
  }

  private String getResourceFilePath(String resourcePath) {
    return getClass().getResource(resourcePath).getFile();
  }

  @After
  public void teardown() throws Exception {
    client.disconnect();
    service.stop(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetUnlockedKeyboardWhenConnect() throws Exception {
    awaitKeyboardUnlock();
    assertThat(client.isKeyboardLocked()).isFalse();
  }

  private void awaitKeyboardUnlock() throws InterruptedException, TimeoutException {
    new UnlockWaiter(client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetWelcomeScreenWhenConnect() throws Exception {
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getWelcomeScreen());
  }

  private String getWelcomeScreen() throws IOException {
    return getFileContent("login-welcome-screen.txt");
  }

  private String getFileContent(String resourceFile) throws IOException {
    return Resources.toString(Resources.getResource(resourceFile),
        Charsets.UTF_8);
  }

  @Test
  public void shouldGetWelcomeScreenWithWrongCharset() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_THREE, SCREEN_DIMENSIONS, LOGIN_SPECIAL_CHARACTERS_FLOW);

    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getFileContent("login-special-character-charset-CP1047.txt"));
  }

  @Test
  public void shouldGetWelcomeScreenWithRightCharset() throws Exception {
    cleanShutdown();
    startServiceWithFlow(LOGIN_SPECIAL_CHARACTERS_FLOW);
    client = new TerminalClient(TERMINAL_MODEL_TYPE_THREE, SCREEN_DIMENSIONS, Charset.CP1047);
    client.setUsesExtended3270(true);
    connectClient();
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getFileContent("login-special-character-charset-CP1047.txt"));
  }

  private void cleanShutdown() throws Exception {
    awaitKeyboardUnlock();
    teardown();
  }

  @Test
  public void shouldGetWelcomeScreenWhenConnectWithSsl() throws Exception {
    setupSslConnection();
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getWelcomeScreen());
  }

  private void setupSslConnection() throws Exception {
    cleanShutdown();

    service.setSslEnabled(true);
    System.setProperty("javax.net.ssl.keyStore", getResourceFilePath("/keystore.jks"));
    System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    service.start();

    client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
    client.setSocketFactory(buildSslContext().getSocketFactory());
    connectClient();
  }

  private SSLContext buildSslContext() throws GeneralSecurityException {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    TrustManager trustManager = new X509TrustManager() {

      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }

      public void checkClientTrusted(
          X509Certificate[] certs, String authType) {
      }

      public void checkServerTrusted(
          X509Certificate[] certs, String authType) {
      }
    };
    sslContext.init(null, new TrustManager[]{trustManager},
        new SecureRandom());
    return sslContext;
  }

  @Test
  public void shouldGetWelcomeScreenWhenConnectWithScreenWithExtendFieldWithoutFieldAttribute()
      throws Exception {
    cleanShutdown();
    startServiceWithFlow("/login-extended-field-without-field-attribute.yml");
    client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
    connectClient();
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getWelcomeScreen());
  }

  @Test
  public void shouldGetUserMenuScreenWhenSendUserFieldByCoord() throws Exception {
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getUserMenuScreen());
  }

  private void sendUserFieldByCoord() {
    sendFieldByCoord(1, 27, USERNAME);
  }

  private void sendFieldByCoord(int row, int column, String text) {
    client.setFieldTextByCoord(row, column, text);
    sendEnter();
  }

  private String getUserMenuScreen() throws IOException {
    return getFileContent("user-menu-screen.txt");
  }

  private void sendEnter() {
    client.sendAID(AIDCommand.AID_ENTER, "ENTER");
  }

  @Test
  public void shouldGetLoginSuccessScreenWhenSendPasswordFieldByProtectedLabel() throws Exception {
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    sendFieldByLabel("Password", PASSWORD);
    awaitSuccessScreen();
  }

  private void sendFieldByLabel(String label, String text) {
    client.setFieldTextByLabel(label, text);
    sendEnter();
  }

  private void awaitSuccessScreen() throws InterruptedException, TimeoutException {
    new ScreenTextWaiter("READY", client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
  }

  @Test
  public void shouldGetUserMenuScreenWhenSendUserFieldByUnprotectedLabel() throws Exception {
    awaitKeyboardUnlock();
    sendFieldByLabel("ENTER USERID", USERNAME);
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getUserMenuScreen());
  }

  @Test
  public void shouldGetWelcomeMessageWhenSendUserInScreenWithoutFields() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/login-without-fields.yml");
    awaitKeyboardUnlock();
    sendFieldByCoord(20, 48, USERNAME);
    awaitKeyboardUnlock();
    sendFieldByCoord(1, 1, USERNAME);
    awaitKeyboardUnlock();
  }

  private void setupExtendedFlow(int terminalType, ScreenDimensions screenDimensions,
      String filePath)
      throws Exception {
    cleanShutdown();
    startServiceWithFlow(filePath);
    client = new TerminalClient(terminalType, screenDimensions);
    client.setUsesExtended3270(true);
    connectClient();
  }

  @Test
  public void shouldGetNotSoundedAlarmWhenWhenConnect() throws Exception {
    awaitKeyboardUnlock();
    assertThat(client.resetAlarm()).isFalse();
  }

  @Test
  public void shouldGetSoundedAlarmWhenWhenSendUserField() throws Exception {
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    assertThat(client.resetAlarm()).isTrue();
  }

  @Test
  public void shouldGetNotSoundedAlarmWhenWhenSendUserFieldAndResetAlarm() throws Exception {
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    client.resetAlarm();
    assertThat(client.resetAlarm()).isFalse();
  }

  @Test
  public void shouldGetFieldPositionWhenGetCursorPositionAfterConnect() throws Exception {
    Point fieldPosition = new Point(1, 2);
    awaitCursorPosition(fieldPosition);
    assertThat(client.getCursorPosition()).isEqualTo(Optional.of(fieldPosition));
  }

  private void awaitCursorPosition(Point position) throws InterruptedException, TimeoutException {
    CountDownLatch latch = new CountDownLatch(1);
    client.addCursorMoveListener((newPos, oldPos, field) -> {
      if (position.equals(client.getCursorPosition().orElse(null))) {
        latch.countDown();
      }
    });
    if (!client.isKeyboardLocked()) {
      latch.countDown();
    }
    if (!latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException();
    }
  }

  @Test
  public void shouldSendExceptionToExceptionHandlerWhenConnectWithInvalidPort() throws Exception {
    client.connect(SERVICE_HOST, 1);
    exceptionWaiter.awaitException();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowIllegalArgumentExceptionWhenSendIncorrectFieldPosition()
      throws Exception {
    awaitKeyboardUnlock();
    client.setFieldTextByCoord(0, 1, "test");
  }

  @Test
  public void shouldSendCloseToExceptionHandlerWhenServerDown() throws Exception {
    awaitKeyboardUnlock();
    service.stop(TIMEOUT_MILLIS);
    exceptionWaiter.awaitClose();
  }

  @Test
  public void shouldSendExceptionToExceptionHandlerWhenSendAndServerDown() throws Exception {
    awaitKeyboardUnlock();
    service.stop(TIMEOUT_MILLIS);
    sendUserFieldByCoord();
    exceptionWaiter.awaitException();
  }

  @Test
  public void shouldGetLoginSuccessScreenWhenLoginWithSscpLuData() throws Exception {
    setupSscpLuLoginFlow();
    awaitKeyboardUnlock();
    sendFieldByCoord(11, 25, APP_NAME);
    awaitKeyboardUnlock();
    client.setFieldTextByCoord(12, 21, USERNAME);
    client.setFieldTextByCoord(13, 21, PASSWORD);
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getSccpLuLoginSuccessScreen());
  }

  private void setupSscpLuLoginFlow() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/sscplu-login.yml");
  }

  private String getSccpLuLoginSuccessScreen() throws IOException {
    return getFileContent("sscplu-login-success-screen.txt");
  }

  @Test
  public void shouldGetCorrectFieldsWhenGetFields() throws Exception {
    when(screenMock.validate(anyInt())).thenAnswer(
        (Answer<Integer>) invocationOnMock -> (Integer) invocationOnMock.getArguments()[0]);
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    assertEquals(expectedFields(), client.getFields());
  }

  private List<Field> expectedFields() {
    ScreenBuilder screenBuilder = new ScreenBuilder()
        .withField(new FieldBuilder("------------------------------- TSO/E LOGON ----------------" +
            "-------------------").withHighIntensity().withSelectorPenDetectable())
        .withField(
            new FieldBuilder("                                                             " +
                "                  ").withHighIntensity().withSelectorPenDetectable())
        .withField(
            new FieldBuilder("                                                             " +
                "                   \u0000\u0000").withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder("Enter LOGON parameters below:" + buildNullString(18))
            .withHighIntensity().withSelectorPenDetectable())
        .withField(
            new FieldBuilder("RACF LOGON parameters:" + buildNullString(88)).withHighIntensity()
                .withSelectorPenDetectable())
        .withField(new FieldBuilder(" Userid    ===>"))
        .withField(new FieldBuilder("TESTUSR ").withHighIntensity())
        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
        .withField(new FieldBuilder(" Seclabel     ===>").withNumeric().withHidden())
        .withField(new FieldBuilder("        ").withNumeric().withHidden())
        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
        .withField(new FieldBuilder(" Password  ===>"))
        .withField(new FieldBuilder("        ").withNotProtected().withHidden())
        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
        .withField(new FieldBuilder(" New Password ===>"))
        .withField(new FieldBuilder("        ").withNotProtected().withHidden())
        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
        .withField(new FieldBuilder(" Procedure ===>"))
        .withField(new FieldBuilder("PROC000 ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(22)).withNumeric())
        .withField(new FieldBuilder(" Group Ident  ===>"))
        .withField(new FieldBuilder("        ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(83)).withNumeric())
        .withField(new FieldBuilder(" Acct Nmbr ===>"))
        .withField(new FieldBuilder("1000000                                 ").withNotProtected()
            .withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(102)).withNumeric())
        .withField(new FieldBuilder(" Size      ===>"))
        .withField(new FieldBuilder("4096   ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(135)).withNumeric())
        .withField(new FieldBuilder(" Perform   ===>"))
        .withField(new FieldBuilder("   ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(139)).withNumeric())
        .withField(new FieldBuilder(" Command   ===>"))
        .withField(new FieldBuilder("                                                            " +
            "                    ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(63)).withNumeric())
        .withField(
            new FieldBuilder("Enter an 'S' before each option desired below:").withHighIntensity()
                .withSelectorPenDetectable())
        .withField(new FieldBuilder(buildNullString(36)))
        .withField(new FieldBuilder(" ").withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder(" ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder("-Nomail").withNumeric())
        .withField(new FieldBuilder("\u0000\u0000\u0000"))
        .withField(new FieldBuilder(" ").withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder(" ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder("-Nonotice").withNumeric())
        .withField(new FieldBuilder("\u0000\u0000"))
        .withField(new FieldBuilder(" ").withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder(" ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder("-Reconnect").withNumeric())
        .withField(new FieldBuilder("\u0000\u0000"))
        .withField(new FieldBuilder(" ").withHighIntensity().withSelectorPenDetectable())
        .withField(new FieldBuilder(" ").withNotProtected().withHighIntensity()
            .withSelectorPenDetectable())
        .withField(new FieldBuilder("-OIDcard ").withNumeric())
        .withField(new FieldBuilder(buildNullString(87)))
        .withField(
            new FieldBuilder("PF1/PF13 ==> Help    PF3/PF15 ==> Logoff    PA1 ==> Attention" +
                "    PA2 ==> Reshow").withHighIntensity().withSelectorPenDetectable())
        .withField(
            new FieldBuilder("You may request specific help information by entering a '?' in" +
                " any entry field\u0000").withHighIntensity().withSelectorPenDetectable());
    return screenBuilder.build();
  }

  private String buildNullString(int count) {
    return new String(new char[count]);
  }

  private static final class ScreenBuilder {

    private final List<Field> fields = new ArrayList<>();

    private Field lastField = null;

    private ScreenBuilder withField(FieldBuilder builder) {
      Field currField = lastField != null ? builder.withStartPosition(lastField.getFirstLocation()
          + lastField.getText().length()).build() : builder.build();
      fields.add(currField);
      lastField = currField;
      return this;
    }

    private List<Field> build() {
      return fields;
    }


  }

  private final class FieldBuilder {

    private int startPosition;

    private final String text;
    private boolean isProtected = true;
    private boolean isNumeric = false;
    private boolean isHidden = false;
    private boolean isHighIntensity = false;
    private boolean isModified = false;
    private boolean selectorPenDetectable = false;

    private FieldBuilder(String text) {
      this.text = text;
    }

    private FieldBuilder withStartPosition(int pos) {
      this.startPosition = pos;
      return this;
    }

    private FieldBuilder withNotProtected() {
      isProtected = false;
      return this;
    }

    private FieldBuilder withNumeric() {
      isNumeric = true;
      return this;
    }

    private FieldBuilder withHidden() {
      isHidden = true;
      return this;
    }

    private FieldBuilder withModified() {
      isModified = true;
      return this;
    }

    private FieldBuilder withHighIntensity() {
      isHighIntensity = true;
      return this;
    }


    private FieldBuilder withSelectorPenDetectable() {
      selectorPenDetectable = true;
      return this;
    }

    private Field build() {
      try {
        List<ScreenPosition> positions = new ArrayList<>();
        for (int i = 0; i <= text.length(); i++) {
          positions.add(
              new ScreenPosition(startPosition + i, ScreenContext.DEFAULT_CONTEXT, Charset.CP1047));
        }
        positions.get(0).setStartField(buildStartFieldAttribute());
        Field f = new Field(TerminalClientTest.this.screenMock, positions);
        f.setText(text.getBytes(Charset.CP1047.name()));
        return f;
      } catch (UnsupportedEncodingException e) {
        // As this is not expected to happen, we just throw RuntimeException.
        throw new RuntimeException(e);
      }
    }

    private StartFieldAttribute buildStartFieldAttribute() {
      byte b = 0;
      if (isProtected) {
        b |= 0x20;
      }
      if (isNumeric) {
        b |= 0x10;
      }
      if (isModified) {
        b |= 0x01;
      }
      if (isHighIntensity) {
        b |= 0x08;
      } else if (isHidden) {
        b |= 0x0C;
      } else if (selectorPenDetectable) {
        b |= 0x04;
      }
      return new StartFieldAttribute(b);
    }

  }

  @Test
  public void shouldShowMenuScreenWithDifferentTerminalType() throws Exception {
    int terminalType = 5;
    setupExtendedFlow(terminalType, new ScreenDimensions(27, 132), "/login-3270-model-5.yml");
    awaitKeyboardUnlock();
    sendUserFieldByCoord();
    awaitKeyboardUnlock();
    assertThat(getScreenText())
        .isEqualTo(getFileContent("user-menu-for-screen-type-5.txt"));
  }

  @Test
  public void shouldSetTextWhenNoScreenFieldsWhileInputByLabel() throws Exception {
    setupSscpLuLoginFlow();
    awaitKeyboardUnlock();
    sendFieldByLabel("APPLICATION NAME", APP_NAME);
    awaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getFileContent("sscplu-login-middle-screen"));
  }

  @Test
  public void shouldGetLoginSuccessScreenWhenEmptyInputByCord() throws Exception {
    setupFlowWithEmptyField();
    awaitKeyboardUnlock();
    sendFieldByCoord(1, 27, "");
    awaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getUserMenuScreen());
  }

  private void setupFlowWithEmptyField() throws Exception {
    cleanShutdown();
    startServiceWithFlow("/login-3270-empty-field.yml");
    client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, Charset.CP1147);
    connectClient();
  }

  @Test
  public void shouldSendTabulatorInput() throws Exception {
    setupSscpLuLoginFlow();
    awaitKeyboardUnlock();
    sendFieldByTab(APP_NAME, 0);
    sendEnterAndWaitKeyboardUnlock();
    sendFieldByTab(USERNAME, 0);
    sendFieldByTab(PASSWORD, 1);
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getSccpLuLoginSuccessScreen());
  }

  public void sendFieldByTab(String text, int offset) throws NoSuchFieldException {
    client.setTabulatedInput(text, offset);
  }

  @Test
  public void shouldSetTabulatorInputWhenCursorPosLacksFieldAndOffsetBiggerThanZero()
      throws Exception {
    awaitKeyboardUnlock();
    sendFieldByCoord(1, 27, "testusr");
    sendEnterAndWaitKeyboardUnlock();
    client.setCursorPosition(50);
    sendFieldByTab("testpsw", 1);
    sendEnterAndWaitKeyboardUnlock();
  }

  @Test
  public void shouldGetSuccessScreenWhenUsingMultipleInputByLabel() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO,SCREEN_DIMENSIONS,
        "/login-3278-M2-E.yml");
    awaitKeyboardUnlock();
    client.setFieldTextByLabel("Userid:", "testusr ");
    client.setFieldTextByLabel("Passcode:", "testpsw");
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getFileContent("login-3278-M2-E-final-screen.txt")).isEqualTo(getScreenText());
  }

  @Test
  public void shouldSuccessfullyLoginWhenAplScreen() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/login-apl-charset-screen.yml");
    awaitKeyboardUnlock();
    sendFieldByTab("TESTUSR", 0);
    sendFieldByTab("TESTPSW", 1);
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getFileContent("success-apl-screen.txt"));
  }

  @Test
  public void shouldSetFieldsWhenFieldsNotHaveStartAttribute() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/field_without_start_attribute.yml");
    awaitKeyboardUnlock();
    sendFieldByTab("TESTUSR", 0);
    sendFieldByTab("TESTPSW", 2);
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getFileContent(
        "field_without_start_attribute_expected_screen.txt"));
  }

  @Test
  public void shouldConnectCorrectlyWhenQueryListEquivalentPlusQCODE() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/test_capabilities.yml");
    awaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getFileContent(
        "field_without_start_attribute_expected_screen.txt"));
  }

  @Test
  public void shouldNotFailWhenFieldAttributeIsNotRecognised() throws Exception {
    setupExtendedFlow(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS, "/attribute_not_present.yml");
    awaitKeyboardUnlock();
    sendFieldByTab("1", 0);
    sendEnterAndWaitKeyboardUnlock();
    assertThat(getScreenText()).isEqualTo(getFileContent(
        "attribute_not_present_expected_screen.txt"));
  }

  private void sendEnterAndWaitKeyboardUnlock() throws TimeoutException, InterruptedException {
    sendEnter();
    awaitKeyboardUnlock();
  }

  @Test
  public void shouldNotNotifyServerDisconnectionWhenClientDisconnect() throws Exception {
    awaitKeyboardUnlock();
    client.addConnectionListener(connectionListenerMock);
    client.disconnect();
    verify(connectionListenerMock, never()).onConnectionClosed();
  }
}
