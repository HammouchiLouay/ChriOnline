package ui;

import common.ClientConfigLoader;
import common.ClientPrefs;
import common.JsonUtil;
import common.Message;
import common.crypto.PemKeyUtil;
import common.crypto.RsaSignatureUtil;
import ecommerce.personne5.model.TypePaiement;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.image.ImageView;
import javafx.scene.text.TextFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import product.Product;

import java.net.InetSocketAddress;
import java.net.URLEncoder;

import server.NetworkInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Client JavaFX ChriOnline — interface alignée sur le serveur socket (pas de mock IA / comparaison / temps réel externe).
 *
 * <p>Fonctions : {@code PING}, {@code LOGIN} / {@code REGISTER} (table MySQL {@code user}), {@code PRODUCT_LIST}
 * / {@code PRODUCT_CATEGORIES}, panier → {@code CREATE_COMMANDE}, {@code GET_COMMANDES}, {@code SIMULATE_PAYMENT}.
 *
 * <p>Les images produits proviennent des URL en base (ex. CDN DummyJSON). Les prix suivent {@code Prix_net_USD}.
 *
 * <p>Lancement : {@code mvn javafx:run} (démarrer d’abord {@link server.ServerMain} sur le port 6000).
 *
 * <p>Configuration optionnelle (classpath + {@code ~/.chrionline/chrionline-client.properties}) : voir {@link ClientConfigLoader}.
 */
public class ChriOnlineClientApp extends Application {

    private static final int DEFAULT_PORT = 6000;
    private static final String PREF_SESSION = "com/chrionline/session";
    private static final String PREF_CLIENT = "com/chrionline/client";
    private static final DateTimeFormatter ORDER_DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private SocketApiClient api;
    /** Merged client properties (classpath + user {@code ~/.chrionline/chrionline-client.properties}). */
    private Properties clientConfig = new Properties();
    /**
     * Si {@code true}, {@code 127.0.0.1} / {@code localhost} sont autorisés (serveur sur la même machine). Réglage
     * {@code client.allow.loopback} ou {@code -Dchrionline.client.allow.loopback}.
     */
    private boolean allowLoopbackHost = true;
    private String host = "";
    private int port = DEFAULT_PORT;
    private int userId = 1;

    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private final List<Product> catalog = new ArrayList<>();
    private Integer lastCreatedCommandeId;

    private Label connectionLabel;
    private Label cartSummaryLabel;
    private TextField hostField;
    private TextField portField;
    private TextField userIdField;
    /** Connexion compte directement dans la barre latérale (évite les soucis de modalité des boîtes de dialogue). */
    private TextField sidebarLoginEmail;
    private PasswordField sidebarLoginPassword;
    private Button sidebarLoginBtn;
    /** Mini-Projet 2 : poignée de main RSA→AES puis lignes chiffrées AES-GCM obligatoires. */
    private Label applicationCryptoStatusLabel;
    private int failedLoginAttemptsThisSession = 0;
    private static final int MAX_LOGIN_ATTEMPTS_PER_SESSION = 5;
    private TextField searchField;
    private TilePane productsGrid;
    private VBox centerHome;
    private VBox centerOrders;
    private VBox centerProduct;
    private VBox centerAccount;
    private VBox centerProfile;
    private VBox centerSeller;
    private VBox centerModeration;
    private StackPane centerStack;
    private Stage primaryStage;
    /** Barre de titre personnalisée : masquée en plein écran pour un rendu type navigateur / app moderne. */
    private HBox chromeTitleBar;

    private Button navAccueilBtn;
    private Button navCatalogueBtn;
    private Button navCommandesBtn;

    private ImageView productDetailImage;
    private Label productDetailTitle;
    private Label productDetailPrice;
    private Label productDetailStock;
    private Label productDetailCategory;
    private Label productDetailBrand;
    private Label productDetailRating;
    private Label productDetailDesc;
    private Label productDetailInCart;
    private Spinner<Integer> productDetailQty;
    private Button productDetailFavBtn;
    private Product currentDetailProduct;

    private ComboBox<String> categoryCombo;
    private Label sessionLabel;
    private Label catalogSubtitleLabel;

    /** Shown when not logged in: Connexion / Inscription. */
    private VBox loggedOutAuthSection;
    /** Shown when logged in: Votre compte + links (reference-style). */
    private VBox loggedInAuthSection;

    private boolean accountLoggedIn;
    /** Dernier test socket (PING) réussi — distinct du compte utilisateur. */
    private boolean serverReachable;
    private String sessionUsername = "";
    /** Jeton émis par le serveur après LOGIN / REGISTER ; obligatoire pour {@code GET_COMMANDES}. */
    private String sessionToken = "";
    /** Rôle depuis LOGIN / REGISTER : {@code CLIENT}, {@code SELLER}, {@code ADMIN}. */
    private String sessionRole = "CLIENT";
    private Hyperlink linkSellerSpace;
    private Hyperlink linkAdminModeration;
    /** Console catalogue admin (UX distincte de la modération). */
    private Hyperlink linkAdminCatalog;
    private VBox centerAdminCatalog;
    private VBox adminCatalogRowsBox;
    private TextField adminProdNomField;
    private TextField adminProdSkuField;
    private TextField adminProdMarqueField;
    private ComboBox<String> adminProdCatCombo;
    private TextField adminProductSearchField;
    private TextField adminProdPrixField;
    private TextField adminProdStockField;
    private TextField adminProdDescField;
    private TextField adminProdImgField;
    private Label accountRoleBadgeLabel;
    private VBox sellerMyListingsBox;
    private VBox moderationPendingBox;
    private TextField sellerNomField;
    private TextField sellerMarqueField;
    private TextField sellerCatField;
    private TextField sellerPrixField;
    private TextField sellerStockField;
    private TextField sellerDescField;
    private TextField sellerImgField;
    private String sessionEmail = "";
    /** Digits only; display only, from last login or profile update. */
    private String sessionPhone = "";
    private boolean sessionEmailVerified;

    private Label accountDispNameLabel;
    private Label accountEmailLabel;
    private Label accountPhoneLabel;
    private PasswordField profilePasswordCurrent;
    private PasswordField profilePasswordNew;
    private TextField profileEmailField;
    private TextField profilePhoneField;
    private TextField profileSecurityOtpField;
    private Label accountEmailVerifyStatusLabel;
    private TextField accountEmailVerifyCodeField;
    private PasswordField deleteAccountPasswordField;
    private TextField deleteAccountOtpField;
    private VBox ordersCardsContainer;

    private static final int CATALOG_PAGE_SIZE = 36;

    private final Set<String> wishlist = new LinkedHashSet<>();
    private final List<SocketApiClient.CommandeFull> commandesDetailCache = new ArrayList<>();
    private final Map<String, Product> productById = new LinkedHashMap<>();

    private String lastInvoiceText = "";
    private int catalogLoadOffset = 0;
    private String currentCatalogCategory = "Tous";
    private volatile boolean catalogLoading = false;
    private volatile boolean catalogHasMore = true;

    private Label topSellerSubtitleLabel;
    private Label dashboardProductsLabel;
    private Label dashboardCategoriesLabel;
    private Label dashboardFavoritesLabel;
    private Label dashboardCartLabel;
    private Label dashboardRevenueLabel;
    private Label dashboardOrdersLabel;
    private Label lastPaymentRiskLabel;
    /** Saved payment methods (right sidebar); rows rebuilt from server. */
    private VBox savedPaymentMethodsBox;
    private ListView<String> favoritesList;
    private TilePane topSellerGrid;
    private ContextMenu searchSuggestions;
    private ProgressIndicator loadingIndicator;
    private StackPane loadingOverlay;
    /** In-app toasts (replaces system Alert for messages). */
    private StackPane toastOverlay;
    private javafx.animation.Animation currentToastAnimation;
    private StackPane rootStack;
    /** Wraps the whole Accueil / Catalogue home content (vertical scroll). */
    private ScrollPane homeScrollPane;
    private VBox homeHeroSection;
    private VBox homeTopSellerSection;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        clientConfig = ClientConfigLoader.load();
        allowLoopbackHost = ClientConfigLoader.isAllowLoopback(clientConfig);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(true);
        // Petits écrans : minimum raisonnable ; la fenêtre s’ouvre dimensionnée sur la zone utile de l’écran.
        final double minW = 720;
        final double minH = 520;
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        api = new SocketApiClient(host, port);

        BorderPane shell = new BorderPane();
        shell.setStyle("-fx-background-color: #070B17;");

        HBox titleBar = buildCustomTitleBar(stage);
        chromeTitleBar = titleBar;

        StackPane backgroundLayer = buildBackground();
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(18));

        ScrollPane leftSidebar = buildSidebar();
        ScrollPane rightPanel = buildRightPanel();

        centerStack = new StackPane();
        centerHome = buildCenterHome();
        centerOrders = buildCenterOrders();
        centerProduct = buildCenterProduct();
        centerAccount = buildCenterAccount();
        centerProfile = buildCenterProfile();
        centerSeller = buildCenterSeller();
        centerModeration = buildCenterModeration();
        centerAdminCatalog = buildCenterAdminCatalog();
        centerStack.getChildren()
                .addAll(
                        centerHome,
                        centerOrders,
                        centerProduct,
                        centerAccount,
                        centerProfile,
                        centerSeller,
                        centerModeration,
                        centerAdminCatalog);
        centerOrders.setVisible(false);
        centerProduct.setVisible(false);
        centerAccount.setVisible(false);
        centerProfile.setVisible(false);
        centerSeller.setVisible(false);
        centerModeration.setVisible(false);
        centerAdminCatalog.setVisible(false);

        content.setLeft(leftSidebar);
        content.setCenter(centerStack);
        content.setRight(rightPanel);

        rootStack = new StackPane();
        toastOverlay = new StackPane();
        toastOverlay.setMouseTransparent(true);
        toastOverlay.setPickOnBounds(false);
        rootStack.getChildren().addAll(backgroundLayer, content, buildLoadingOverlay(), toastOverlay);
        shell.setTop(titleBar);
        shell.setCenter(rootStack);

        Rectangle2D vis = Screen.getPrimary().getVisualBounds();
        double margin = 32;
        double maxW = Math.max(minW, vis.getWidth() - margin);
        double maxH = Math.max(minH, vis.getHeight() - margin);
        double initW = Math.min(maxW, Math.max(minW, vis.getWidth() * 0.88));
        double initH = Math.min(maxH, Math.max(minH, vis.getHeight() * 0.82));
        Scene scene = new Scene(shell, initW, initH);

        stage.setTitle("ChriOnline");
        for (int iconSize : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(BrandIconUtil.createFxImageForStage(iconSize));
        }
        stage.setScene(scene);
        installDarkScrollBarStyle(scene);
        StageResizeBehavior.install(stage, scene);

        stage.fullScreenProperty()
                .addListener(
                        (obs, was, fs) -> {
                            if (chromeTitleBar != null) {
                                chromeTitleBar.setVisible(!fs);
                                chromeTitleBar.setManaged(!fs);
                            }
                        });

        scene.widthProperty().addListener((obs, o, n) -> layoutTileColumns(n.doubleValue()));
        scene.heightProperty().addListener((obs, o, n) -> layoutTileColumns(scene.getWidth()));
        layoutTileColumns(scene.getWidth());

        scene.addEventFilter(
                KeyEvent.KEY_PRESSED,
                e -> {
                    if (e.getCode() == KeyCode.F11) {
                        stage.setFullScreen(!stage.isFullScreen());
                        e.consume();
                    } else if (e.getCode() == KeyCode.ESCAPE && stage.isFullScreen()) {
                        stage.setFullScreen(false);
                        e.consume();
                    }
                });

        stage.centerOnScreen();
        stage.show();
        layoutTileColumns(scene.getWidth());

        playEntrance(leftSidebar, centerStack, rightPanel);

        rebuildApiFromFields();
        refreshSessionBanner();

        restorePersistedSession();

        Platform.runLater(this::discoverAndConnect);

        setNavState(0);
    }

    /** Dark chrome bar (browser-style): drag to move, minimize / maximize / close. */
    private HBox buildCustomTitleBar(Stage stage) {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 2, 0, 0));
        bar.setMinHeight(40);
        bar.setPrefHeight(40);
        bar.setStyle(
                "-fx-background-color: #0b0e14;"
                        + "-fx-border-color: rgba(255,255,255,0.08);"
                        + "-fx-border-width: 0 0 1 0;");

        HBox tabPill = new HBox(8);
        tabPill.setAlignment(Pos.CENTER_LEFT);
        tabPill.setPadding(new Insets(4, 12, 4, 10));
        tabPill.setStyle(
                "-fx-background-color: rgba(28,36,54,0.95);"
                        + "-fx-background-radius: 10 10 0 0;"
                        + "-fx-border-color: rgba(255,255,255,0.10);"
                        + "-fx-border-radius: 10 10 0 0;");
        ImageView titleIcon = new ImageView(BrandIconUtil.createFxImageTitleTab(64));
        titleIcon.setFitHeight(26);
        titleIcon.setFitWidth(26);
        titleIcon.setPreserveRatio(true);
        titleIcon.setSmooth(true);
        TextFlow titleBrand = new TextFlow();
        Label shopPart = new Label("Chri");
        shopPart.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        shopPart.setTextFill(Color.WHITE);
        Label onlinePart = new Label("Online");
        onlinePart.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        onlinePart.setTextFill(Color.web("#F37021"));
        titleBrand.getChildren().addAll(shopPart, onlinePart);
        tabPill.getChildren().addAll(titleIcon, titleBrand);

        Button newTabBtn = new Button("+");
        newTabBtn.setStyle(
                "-fx-background-color: transparent;"
                        + "-fx-text-fill: #8899bb;"
                        + "-fx-font-size: 16px;"
                        + "-fx-min-width: 28;"
                        + "-fx-min-height: 28;");
        newTabBtn.setFocusTraversable(false);

        Region dragArea = new Region();
        HBox.setHgrow(dragArea, Priority.ALWAYS);

        HBox leftDrag = new HBox(6, tabPill, newTabBtn, dragArea);
        leftDrag.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftDrag, Priority.ALWAYS);

        Button minBtn = newTitleChromeButton("─");
        Button maxBtn = newTitleChromeButton("□");
        maxBtn.setMinWidth(46);
        Button closeBtn = newTitleCloseButton();

        Runnable syncMaxGlyph = () -> maxBtn.setText(stage.isMaximized() ? "❐" : "□");
        stage.maximizedProperty().addListener((o, a, b) -> syncMaxGlyph.run());
        syncMaxGlyph.run();

        minBtn.setOnAction(e -> stage.setIconified(true));
        maxBtn.setOnAction(
                e -> {
                    if (stage.isFullScreen()) {
                        stage.setFullScreen(false);
                    }
                    stage.setMaximized(!stage.isMaximized());
                });
        closeBtn.setOnAction(e -> Platform.exit());

        final double[] dragOffset = new double[2];
        leftDrag.setOnMousePressed(
                e -> {
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    if (stage.isMaximized()) {
                        return;
                    }
                    dragOffset[0] = e.getScreenX() - stage.getX();
                    dragOffset[1] = e.getScreenY() - stage.getY();
                });
        leftDrag.setOnMouseDragged(
                e -> {
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    if (stage.isMaximized()) {
                        return;
                    }
                    stage.setX(e.getScreenX() - dragOffset[0]);
                    stage.setY(e.getScreenY() - dragOffset[1]);
                });
        leftDrag.setOnMouseClicked(
                e -> {
                    if (e.getClickCount() == 2) {
                        if (stage.isFullScreen()) {
                            stage.setFullScreen(false);
                        } else {
                            stage.setMaximized(!stage.isMaximized());
                        }
                    }
                });

        newTabBtn.setOnMouseClicked(MouseEvent::consume);

        Label chevron = new Label("⌄");
        chevron.setTextFill(Color.web("#6b7a95"));
        chevron.setPadding(new Insets(0, 12, 0, 4));

        HBox right = new HBox(0, chevron, minBtn, maxBtn, closeBtn);
        right.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(leftDrag, right);
        HBox.setHgrow(leftDrag, Priority.ALWAYS);
        return bar;
    }

    private static Button newTitleChromeButton(String text) {
        Button b = new Button(text);
        String normal =
                "-fx-background-color: transparent;"
                        + "-fx-text-fill: #c8d0e0;"
                        + "-fx-font-size: 13px;"
                        + "-fx-min-width: 46;"
                        + "-fx-min-height: 28;"
                        + "-fx-background-radius: 0;";
        b.setStyle(normal);
        b.setFocusTraversable(false);
        b.setOnMouseEntered(e -> b.setStyle(normal + "-fx-background-color: rgba(255,255,255,0.08);"));
        b.setOnMouseExited(e -> b.setStyle(normal));
        return b;
    }

    private static Button newTitleCloseButton() {
        Button b = new Button("✕");
        String base =
                "-fx-background-color: transparent;"
                        + "-fx-text-fill: #c8d0e0;"
                        + "-fx-font-size: 12px;"
                        + "-fx-min-width: 46;"
                        + "-fx-min-height: 28;"
                        + "-fx-background-radius: 0;";
        b.setStyle(base);
        b.setFocusTraversable(false);
        b.setOnMouseEntered(e -> b.setStyle(base + "-fx-background-color: #e81123; -fx-text-fill: white;"));
        b.setOnMouseExited(e -> b.setStyle(base));
        return b;
    }

    private StackPane buildLoadingOverlay() {
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(56, 56);
        Label lbl = new Label("Chargement…");
        lbl.setTextFill(Color.WHITE);
        lbl.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        VBox box = new VBox(12, loadingIndicator, lbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle(
                "-fx-background-color: rgba(10,14,24,0.82);"
                        + "-fx-background-radius: 20;"
                        + "-fx-border-color: rgba(255,255,255,0.10);"
                        + "-fx-border-radius: 20;"
        );
        loadingOverlay = new StackPane(box);
        loadingOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.22);");
        loadingOverlay.setVisible(false);
        loadingOverlay.setMouseTransparent(true);
        return loadingOverlay;
    }

    private void showLoading(boolean on) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(on);
            loadingOverlay.setMouseTransparent(!on);
        }
    }

    /** Adapts how many product tiles fit per row (sidebar / panier widths reserved). */
    private void layoutTileColumns(double sceneWidth) {
        double reserved = 250 + 330 + 80;
        double slot = 248;
        double center = Math.max(320, sceneWidth - reserved);
        int cols = (int) Math.max(2, Math.min(12, center / slot));
        if (productsGrid != null) {
            productsGrid.setPrefColumns(cols);
        }
        if (topSellerGrid != null) {
            topSellerGrid.setPrefColumns(Math.max(2, Math.min(4, cols - 1)));
        }
    }

    private StackPane buildBackground() {
        StackPane stack = new StackPane();
        Region base = new Region();
        base.setStyle("-fx-background-color: linear-gradient(to bottom right, #050814, #091126, #05070F);");
        base.prefWidthProperty().bind(stack.widthProperty());
        base.prefHeightProperty().bind(stack.heightProperty());

        Circle glow1 = new Circle(260, Color.web("#2457FF", 0.18));
        glow1.setTranslateX(-260);
        glow1.setTranslateY(-280);
        glow1.setEffect(new GaussianBlur(120));

        Circle glow2 = new Circle(220, Color.web("#8B5CF6", 0.16));
        glow2.setTranslateX(420);
        glow2.setTranslateY(-240);
        glow2.setEffect(new GaussianBlur(120));

        Circle glow3 = new Circle(180, Color.web("#18C964", 0.10));
        glow3.setTranslateX(260);
        glow3.setTranslateY(240);
        glow3.setEffect(new GaussianBlur(100));

        stack.getChildren().addAll(base, glow1, glow2, glow3);
        return stack;
    }

    private ScrollPane buildSidebar() {
        VBox sidebar = createGlassBox(14, 16);
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setMaxWidth(240);

        ImageView sidebarBrandIcon = new ImageView(BrandIconUtil.createFxImage(128));
        sidebarBrandIcon.setFitHeight(50);
        sidebarBrandIcon.setFitWidth(50);
        sidebarBrandIcon.setPreserveRatio(true);
        sidebarBrandIcon.setSmooth(true);
        TextFlow sidebarWordmark = new TextFlow();
        Label spShop = new Label("Chri");
        spShop.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        spShop.setTextFill(Color.WHITE);
        Label spOn = new Label("Online");
        spOn.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        spOn.setTextFill(Color.web("#F37021"));
        sidebarWordmark.getChildren().addAll(spShop, spOn);
        Label deliver = new Label("Le bonheur livré chez vous");
        deliver.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        deliver.setTextFill(Color.web("#A8A8A8"));
        VBox brandWords = new VBox(2, sidebarWordmark, deliver);
        HBox logoRow = new HBox(12, sidebarBrandIcon, brandWords);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        Label slogan = new Label("Produits · Commandes · Paiement · F11 plein écran");
        slogan.setTextFill(Color.web("#8FA2C9"));
        slogan.setStyle("-fx-font-size: 10px;");

        connectionLabel = new Label("Hors ligne");
        connectionLabel.setTextFill(Color.web("#FF8A8A"));

        sessionLabel = new Label("");
        sessionLabel.setWrapText(true);
        sessionLabel.setTextFill(Color.web("#A8B4D1"));
        sessionLabel.setFont(Font.font(11));

        navAccueilBtn = createNavButton("Accueil", () -> showAccueil());
        navCatalogueBtn = createNavButton("Catalogue", () -> showCatalogueBrowse());
        navCommandesBtn = createNavButton("Commandes", () -> showOrders());
        VBox menu = new VBox(8, navAccueilBtn, navCatalogueBtn, navCommandesBtn);

        Label srv = createSectionLabel("SERVEUR (socket — pas MySQL)");
        Label srvHint =
                new Label(
                        allowLoopbackHost
                                ? "MySQL reste sur le PC qui exécute le serveur. Sur cette machine : 127.0.0.1 ou "
                                        + "localhost. Depuis un autre poste : IPv4 LAN du serveur ou un domaine."
                                : "MySQL reste sur le PC serveur. Utilisez une IPv4 LAN ou un domaine "
                                        + "(localhost désactivé : client.allow.loopback=false).");
        srvHint.setWrapText(true);
        srvHint.setTextFill(Color.web("#7A869F"));
        srvHint.setFont(Font.font(9));
        hostField = new TextField(allowLoopbackHost ? "127.0.0.1" : "");
        hostField.setPromptText(
                allowLoopbackHost
                        ? "127.0.0.1 (ici) ou IP LAN / domaine"
                        : "IP ou hostname du serveur (ex. 192.168.x.x)");
        hostField.setStyle(inputStyle());
        portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setStyle(inputStyle());
        portField.setPrefWidth(80);

        HBox hostRow = new HBox(8, new Label("Hôte:"), hostField);
        hostRow.setAlignment(Pos.CENTER_LEFT);
        Label pl = new Label("Port:");
        pl.setTextFill(Color.web("#A8B4D1"));
        HBox portRow = new HBox(8, pl, portField);

        Button connectBtn = createGlowButton("Connecter", "#2962FF");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setOnAction(e -> connectToServer());

        applicationCryptoStatusLabel = new Label("Sécurité socket : RSA→AES obligatoire");
        applicationCryptoStatusLabel.setWrapText(true);
        applicationCryptoStatusLabel.setTextFill(Color.web("#CBD5E1"));
        applicationCryptoStatusLabel.setFont(Font.font(11));
        applicationCryptoStatusLabel.setTooltip(
                new Tooltip(
                        "Le mode RSA/AES est maintenant le protocole standard.\n"
                                + "Étape 1 : le serveur envoie la clé publique RSA.\n"
                                + "Étape 2 : le client génère une clé AES et l’envoie chiffrée.\n"
                                + "Étape 3 : tout le trafic JSON passe en AES-GCM."));

        Label auth = createSectionLabel("COMPTE");
        Label authHint =
                new Label(
                        "Après « Connecter », saisissez vos identifiants ici (pas seulement la liaison serveur).");
        authHint.setWrapText(true);
        authHint.setTextFill(Color.web("#64748B"));
        authHint.setFont(Font.font(9));
        sidebarLoginEmail = new TextField();
        sidebarLoginEmail.setPromptText("E-mail ou n° téléphone");
        sidebarLoginEmail.setStyle(inputStyle());
        sidebarLoginEmail.setMaxWidth(Double.MAX_VALUE);
        sidebarLoginPassword = new PasswordField();
        sidebarLoginPassword.setPromptText("Mot de passe");
        sidebarLoginPassword.setStyle(inputStyle());
        sidebarLoginPassword.setMaxWidth(Double.MAX_VALUE);
        sidebarLoginBtn = createGlowButton("Se connecter", "#2962FF");
        sidebarLoginBtn.setMaxWidth(Double.MAX_VALUE);
        sidebarLoginBtn.setOnAction(e -> submitSidebarLogin());
        sidebarLoginPassword.setOnAction(e -> submitSidebarLogin());
        Hyperlink forgotSidebar = new Hyperlink("Mot de passe oublié ?");
        forgotSidebar.setTextFill(Color.web("#93c5fd"));
        forgotSidebar.setWrapText(true);
        forgotSidebar.setOnAction(e -> openForgotPasswordDialog());
        Hyperlink adminRsaLogin = new Hyperlink("Connexion admin (RSA, sans mot de passe)");
        adminRsaLogin.setTextFill(Color.web("#c4b5fd"));
        adminRsaLogin.setWrapText(true);
        adminRsaLogin.setOnAction(e -> openAdminRsaLoginDialog());
        Button regBtn = createOutlineButton("Inscription");
        regBtn.setMaxWidth(Double.MAX_VALUE);
        regBtn.setOnAction(e -> openRegisterDialog());

        loggedOutAuthSection =
                new VBox(
                        8,
                        auth,
                        authHint,
                        sidebarLoginEmail,
                        sidebarLoginPassword,
                        sidebarLoginBtn,
                        forgotSidebar,
                        adminRsaLogin,
                        regBtn);

        loggedInAuthSection = buildLoggedInAccountSection();
        loggedInAuthSection.setVisible(false);
        loggedInAuthSection.setManaged(false);

        sidebar.getChildren()
                .addAll(
                        new VBox(4, logoRow, slogan, connectionLabel, sessionLabel),
                        menu,
                        srv,
                        srvHint,
                        hostRow,
                        portRow,
                        connectBtn,
                        applicationCryptoStatusLabel,
                        loggedOutAuthSection,
                        loggedInAuthSection
                );

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setMinViewportHeight(0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.setMaxWidth(240);
        scroll.setPrefWidth(240);
        scroll.setMinWidth(240);
        return scroll;
    }

    /** Sidebar links for logged-in users (all backed by the socket server). */
    private VBox buildLoggedInAccountSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(4, 0, 0, 0));

        Label section = createSectionLabel("COMPTE");
        Label title = new Label("Votre compte");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);

        Button logoutBtn = createLogoutSidebarButton();
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> logoutAccount());

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: rgba(255,255,255,0.12);");

        Hyperlink linkAccount = accountSidebarLink("Compte");
        linkAccount.setOnAction(e -> showAccountPage());

        Hyperlink linkEdit = accountSidebarLink("Modifier le profil");
        linkEdit.setOnAction(e -> showProfilePage());

        Hyperlink linkOrders = accountSidebarLink("Commandes");
        linkOrders.setOnAction(e -> showOrders());

        linkSellerSpace = accountSidebarLink("Espace vendeur");
        linkSellerSpace.setOnAction(e -> showSellerPage());
        linkSellerSpace.setVisible(false);
        linkSellerSpace.setManaged(false);

        linkAdminModeration = accountSidebarLink("Modération catalogue");
        linkAdminModeration.setOnAction(e -> showAdminModerationPage());
        linkAdminModeration.setVisible(false);
        linkAdminModeration.setManaged(false);

        linkAdminCatalog = accountSidebarLink("Console catalogue (admin)");
        linkAdminCatalog.setStyle(
                "-fx-border-color: transparent; -fx-padding: 4 0 4 0; -fx-font-size: 13px; -fx-text-fill: #fcd34d;");
        linkAdminCatalog.setOnMouseEntered(ev -> linkAdminCatalog.setTextFill(Color.web("#fde68a")));
        linkAdminCatalog.setOnMouseExited(ev -> linkAdminCatalog.setTextFill(Color.web("#fcd34d")));
        linkAdminCatalog.setOnAction(e -> showAdminCatalogPage());
        linkAdminCatalog.setVisible(false);
        linkAdminCatalog.setManaged(false);

        box.getChildren()
                .addAll(
                        section,
                        title,
                        logoutBtn,
                        sep,
                        linkAccount,
                        linkEdit,
                        linkOrders,
                        linkSellerSpace,
                        linkAdminModeration,
                        linkAdminCatalog);
        return box;
    }

    /** Full-width logout — placed under “Votre compte” so it stays visible without scrolling past all links. */
    private Button createLogoutSidebarButton() {
        Button b = new Button("Se déconnecter");
        b.setTextFill(Color.WHITE);
        b.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        b.setStyle(
                "-fx-background-radius: 12; -fx-background-color: rgba(220,38,38,0.35); "
                        + "-fx-border-color: rgba(248,113,113,0.55); -fx-border-radius: 12; -fx-padding: 10 14 10 14;"
        );
        b.setOnMouseEntered(
                e -> b.setStyle(
                        "-fx-background-radius: 12; -fx-background-color: rgba(220,38,38,0.5); "
                                + "-fx-border-color: rgba(252,165,165,0.7); -fx-border-radius: 12; -fx-padding: 10 14 10 14;"));
        b.setOnMouseExited(
                e -> b.setStyle(
                        "-fx-background-radius: 12; -fx-background-color: rgba(220,38,38,0.35); "
                                + "-fx-border-color: rgba(248,113,113,0.55); -fx-border-radius: 12; -fx-padding: 10 14 10 14;"));
        return b;
    }

    private static Hyperlink accountSidebarLink(String text) {
        Hyperlink h = new Hyperlink(text);
        h.setWrapText(true);
        h.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
        h.setMaxWidth(Double.MAX_VALUE);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-border-color: transparent; -fx-padding: 4 0 4 0; -fx-font-size: 13px;");
        h.setTextFill(Color.web("#93c5fd"));
        h.setOnMouseEntered(ev -> h.setTextFill(Color.web("#bfdbfe")));
        h.setOnMouseExited(ev -> h.setTextFill(Color.web("#93c5fd")));
        return h;
    }

    private void logoutAccount() {
        String tok = sessionToken;
        sessionToken = "";
        sessionRole = "CLIENT";
        sessionUsername = "";
        sessionEmail = "";
        sessionPhone = "";
        sessionEmailVerified = false;
        clearPersistedSession();
        if (api != null && host != null && !host.isBlank() && tok != null && !tok.isEmpty()) {
            runAsync(
                    () -> {
                        try {
                            api.send(
                                    Message.request(
                                            "LOGOUT",
                                            "lo",
                                            "{\"sessionToken\":\"" + jsonEsc(tok) + "\"}"));
                        } catch (Exception ignored) {
                            // déconnexion locale prioritaire
                        } finally {
                            if (api != null) {
                                api.closeQuietly();
                            }
                        }
                    });
        } else if (api != null) {
            api.closeQuietly();
        }
        if (userIdField != null) {
            userIdField.setText("1");
            userId = 1;
        }
        setAccountLoggedInUi(false);
        updateSessionRoleNav();
        refreshSessionBanner();
    }

    /**
     * Le serveur ne reconnaît plus le jeton (redémarrage, autre instance, révocation). Nettoie l’état local sans
     * appeler {@code LOGOUT} (inutile).
     */
    private void clearLocalSessionBecauseInvalidOnServer() {
        if (api != null) {
            api.closeQuietly();
        }
        sessionToken = "";
        sessionRole = "CLIENT";
        sessionUsername = "";
        sessionEmail = "";
        sessionPhone = "";
        sessionEmailVerified = false;
        clearPersistedSession();
        if (userIdField != null) {
            userIdField.setText("1");
            userId = 1;
        }
        setAccountLoggedInUi(false);
        updateSessionRoleNav();
        refreshSessionBanner();
        refreshAccountLabels();
        commandesDetailCache.clear();
        rebuildOrdersCards();
        updateDashboardStats();
    }

    private void updateSessionRoleNav() {
        boolean seller = "SELLER".equalsIgnoreCase(sessionRole);
        boolean admin = "ADMIN".equalsIgnoreCase(sessionRole);
        if (linkSellerSpace != null) {
            linkSellerSpace.setVisible(seller);
            linkSellerSpace.setManaged(seller);
        }
        if (linkAdminModeration != null) {
            linkAdminModeration.setVisible(admin);
            linkAdminModeration.setManaged(admin);
        }
        if (linkAdminCatalog != null) {
            linkAdminCatalog.setVisible(admin);
            linkAdminCatalog.setManaged(admin);
        }
    }

    private void setAccountLoggedInUi(boolean loggedIn) {
        accountLoggedIn = loggedIn;
        if (loggedOutAuthSection != null) {
            loggedOutAuthSection.setVisible(!loggedIn);
            loggedOutAuthSection.setManaged(!loggedIn);
        }
        if (loggedInAuthSection != null) {
            loggedInAuthSection.setVisible(loggedIn);
            loggedInAuthSection.setManaged(loggedIn);
        }
    }

    private void showOnlyCenter(javafx.scene.Node visible) {
        centerHome.setVisible(visible == centerHome);
        centerOrders.setVisible(visible == centerOrders);
        if (centerProduct != null) {
            centerProduct.setVisible(visible == centerProduct);
        }
        if (centerAccount != null) {
            centerAccount.setVisible(visible == centerAccount);
        }
        if (centerProfile != null) {
            centerProfile.setVisible(visible == centerProfile);
        }
        if (centerSeller != null) {
            centerSeller.setVisible(visible == centerSeller);
        }
        if (centerModeration != null) {
            centerModeration.setVisible(visible == centerModeration);
        }
        if (centerAdminCatalog != null) {
            centerAdminCatalog.setVisible(visible == centerAdminCatalog);
        }
    }

    private void showAccueil() {
        showOnlyCenter(centerHome);
        applyHomeBrowseMode(false);
        setNavState(0);
    }

    /** Catalogue seul : masque hero et Top Sellers. */
    private void showCatalogueBrowse() {
        showOnlyCenter(centerHome);
        applyHomeBrowseMode(true);
        setNavState(1);
        Platform.runLater(
                () -> {
                    if (homeScrollPane != null) {
                        homeScrollPane.setVvalue(0);
                    }
                });
    }

    private void showAccountPage() {
        refreshAccountLabels();
        showOnlyCenter(centerAccount);
        clearNavHighlight();
    }

    private void showProfilePage() {
        if (profileEmailField != null) {
            profileEmailField.setText(sessionEmail);
        }
        if (profilePhoneField != null) {
            profilePhoneField.setText(sessionPhone);
        }
        if (profilePasswordCurrent != null) {
            profilePasswordCurrent.clear();
        }
        if (profilePasswordNew != null) {
            profilePasswordNew.clear();
        }
        if (profileSecurityOtpField != null) {
            profileSecurityOtpField.clear();
        }
        showOnlyCenter(centerProfile);
        clearNavHighlight();
    }

    private void clearNavHighlight() {
        if (navAccueilBtn == null) {
            return;
        }
        applyNavStyle(navAccueilBtn, false);
        applyNavStyle(navCatalogueBtn, false);
        applyNavStyle(navCommandesBtn, false);
    }

    private void refreshAccountLabels() {
        if (accountDispNameLabel != null) {
            accountDispNameLabel.setText(sessionUsername.isEmpty() ? "—" : sessionUsername);
        }
        if (accountEmailLabel != null) {
            accountEmailLabel.setText(sessionEmail.isEmpty() ? "—" : sessionEmail);
        }
        if (accountPhoneLabel != null) {
            accountPhoneLabel.setText(sessionPhone.isEmpty() ? "—" : sessionPhone);
        }
        if (accountEmailVerifyStatusLabel != null) {
            if (sessionEmailVerified) {
                accountEmailVerifyStatusLabel.setText("Votre adresse e-mail est vérifiée.");
                accountEmailVerifyStatusLabel.setTextFill(Color.web("#8DF0BC"));
            } else {
                accountEmailVerifyStatusLabel.setText(
                        "Adresse non vérifiée — demandez un code, puis saisissez-le ci-dessous.");
                accountEmailVerifyStatusLabel.setTextFill(Color.web("#FBBF24"));
            }
        }
        if (accountRoleBadgeLabel != null) {
            String r = sessionRole != null ? sessionRole.trim() : "CLIENT";
            if ("ADMIN".equalsIgnoreCase(r)) {
                accountRoleBadgeLabel.setText("ADMINISTRATEUR — accès console catalogue et modération");
                accountRoleBadgeLabel.setTextFill(Color.web("#FFFBEB"));
                accountRoleBadgeLabel.setStyle(
                        "-fx-background-color: rgba(245,158,11,0.28); -fx-background-radius: 10; "
                                + "-fx-border-color: rgba(251,191,36,0.85); -fx-border-radius: 10; -fx-padding: 10 14;");
            } else if ("SELLER".equalsIgnoreCase(r)) {
                accountRoleBadgeLabel.setText("Vendeur — publication sous validation admin");
                accountRoleBadgeLabel.setTextFill(Color.web("#E0F2FE"));
                accountRoleBadgeLabel.setStyle(
                        "-fx-background-color: rgba(56,189,248,0.12); -fx-background-radius: 10; "
                                + "-fx-border-color: rgba(125,211,252,0.45); -fx-border-radius: 10; -fx-padding: 8 12;");
            } else {
                accountRoleBadgeLabel.setText("Client");
                accountRoleBadgeLabel.setTextFill(Color.web("#94a3b8"));
                accountRoleBadgeLabel.setStyle("-fx-background-color: transparent; -fx-padding: 4 0;");
            }
        }
    }

    private void applyHomeBrowseMode(boolean catalogueOnly) {
        if (homeHeroSection != null) {
            homeHeroSection.setVisible(!catalogueOnly);
            homeHeroSection.setManaged(!catalogueOnly);
        }
        if (homeTopSellerSection != null) {
            homeTopSellerSection.setVisible(!catalogueOnly);
            homeTopSellerSection.setManaged(!catalogueOnly);
        }
    }

    private void showOrders() {
        showOnlyCenter(centerOrders);
        setNavState(2);
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            showAppToast("Connectez-vous pour consulter vos commandes.", Alert.AlertType.INFORMATION);
        }
        refreshCommandes();
    }

    /** Fiche produit plein écran (remplace l’ancienne boîte de dialogue). */
    private void showProductDetail(Product p) {
        Product fresh = findProduct(p.getId());
        currentDetailProduct = fresh != null ? fresh : p;
        showOnlyCenter(centerProduct);
        setNavState(1);
        refreshProductDetailView();
    }

    private void setNavState(int index) {
        if (navAccueilBtn == null) {
            return;
        }
        applyNavStyle(navAccueilBtn, index == 0);
        applyNavStyle(navCatalogueBtn, index == 1);
        applyNavStyle(navCommandesBtn, index == 2);
    }

    private void applyNavStyle(Button btn, boolean active) {
        btn.setStyle(
                "-fx-background-radius: 14;"
                        + (active
                        ? "-fx-background-color: linear-gradient(to right, rgba(41,98,255,0.95), rgba(64,105,255,0.75));"
                        : "-fx-background-color: rgba(255,255,255,0.03);")
                        + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 14;"
        );
        addButtonHover(btn, active ? 1.02 : 1.03);
    }

    private VBox buildCenterProduct() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(0, 12, 0, 12));
        root.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour au catalogue");
        back.setOnAction(e -> showCatalogueBrowse());

        productDetailTitle = new Label();
        productDetailTitle.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 26));
        productDetailTitle.setTextFill(Color.WHITE);
        productDetailTitle.setWrapText(true);

        StackPane imgBox = new StackPane();
        imgBox.setMaxWidth(440);
        Rectangle imgPh = new Rectangle(420, 280);
        imgPh.setArcWidth(24);
        imgPh.setArcHeight(24);
        imgPh.setFill(
                new LinearGradient(
                        0,
                        0,
                        1,
                        1,
                        true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#1e293b", 0.95)),
                        new Stop(1, Color.web("#0f172a", 0.98))));
        imgPh.setStroke(Color.web("#FFFFFF", 0.1));
        productDetailImage = new ImageView();
        productDetailImage.setFitWidth(420);
        productDetailImage.setFitHeight(280);
        productDetailImage.setPreserveRatio(true);
        imgBox.getChildren().addAll(imgPh, productDetailImage);

        productDetailPrice = themedDetailLabel("", 22, FontWeight.EXTRA_BOLD);
        productDetailStock = themedDetailLabel("", 14, FontWeight.NORMAL);
        productDetailCategory = themedDetailLabel("", 13, FontWeight.NORMAL);
        productDetailBrand = themedDetailLabel("", 13, FontWeight.NORMAL);
        productDetailRating = themedDetailLabel("", 14, FontWeight.NORMAL);
        productDetailInCart = themedDetailLabel("", 13, FontWeight.NORMAL);

        productDetailDesc = new Label();
        productDetailDesc.setWrapText(true);
        productDetailDesc.setTextFill(Color.web("#B8C5E0"));
        productDetailDesc.setFont(Font.font(14));
        productDetailDesc.setMaxWidth(640);

        productDetailQty = new Spinner<>(1, 1, 1);
        productDetailQty.setEditable(true);
        productDetailQty.setPrefWidth(110);
        styleSpinner(productDetailQty);

        productDetailFavBtn = createOutlineButton("Ajouter aux favoris");
        productDetailFavBtn.setMaxWidth(220);
        productDetailFavBtn.setOnAction(
                e -> {
                    if (currentDetailProduct != null) {
                        toggleWishlist(currentDetailProduct.getId());
                        refreshProductDetailView();
                    }
                });

        Button addCart = createGlowButton("Ajouter au panier", "#2962FF");
        addCart.setMaxWidth(Double.MAX_VALUE);
        addCart.setOnAction(e -> addProductDetailToCart());

        HBox qtyRow = new HBox(12);
        qtyRow.setAlignment(Pos.CENTER_LEFT);
        Label qtyLbl = new Label("Quantité");
        qtyLbl.setTextFill(Color.web("#A8B4D1"));
        qtyRow.getChildren().addAll(qtyLbl, productDetailQty);

        HBox actions = new HBox(12, productDetailFavBtn, addCart);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label descHeader = new Label("Description");
        descHeader.setTextFill(Color.web("#7183A8"));
        descHeader.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        VBox card = createGlassBox(16, 20);
        card.getChildren()
                .addAll(
                        back,
                        productDetailTitle,
                        imgBox,
                        productDetailPrice,
                        productDetailStock,
                        productDetailCategory,
                        productDetailBrand,
                        productDetailRating,
                        productDetailInCart,
                        descHeader,
                        productDetailDesc,
                        qtyRow,
                        actions);

        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
        return root;
    }

    private VBox buildCenterAccount() {
        VBox scrollContent = new VBox(18);
        scrollContent.setPadding(new Insets(0, 12, 0, 12));
        scrollContent.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour à l’accueil");
        back.setOnAction(e -> showAccueil());

        Label title = new Label("Détails du compte");
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        title.setTextFill(Color.WHITE);

        VBox card = createGlassBox(16, 20);
        Label hint = new Label("Vous pouvez mettre à jour ces informations depuis « Modifier le profil ».");
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#9DB0D4"));
        hint.setFont(Font.font(12));

        Label lRole = sectionKicker("Rôle du compte");
        accountRoleBadgeLabel = new Label("Client");
        accountRoleBadgeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        accountRoleBadgeLabel.setWrapText(true);

        accountDispNameLabel = themedDetailLabel("", 16, FontWeight.BOLD);
        accountEmailLabel = themedDetailLabel("", 14, FontWeight.NORMAL);
        accountPhoneLabel = themedDetailLabel("", 14, FontWeight.NORMAL);

        Label lName = sectionKicker("Nom affiché");
        Label lEmail = sectionKicker("E-mail");
        Label lPhone = sectionKicker("Téléphone");
        Label lVerify = sectionKicker("Vérification e-mail");
        accountEmailVerifyStatusLabel = new Label();
        accountEmailVerifyStatusLabel.setWrapText(true);
        accountEmailVerifyStatusLabel.setFont(Font.font(11));
        accountEmailVerifyCodeField = new TextField();
        accountEmailVerifyCodeField.setPromptText("Code à 6 chiffres");
        accountEmailVerifyCodeField.setStyle(inputStyle());
        Button sendVerifyBtn = createOutlineButton("Envoyer un code");
        sendVerifyBtn.setMaxWidth(200);
        sendVerifyBtn.setOnAction(e -> sendAccountEmailVerification());
        Button confirmVerifyBtn = createOutlineButton("Confirmer");
        confirmVerifyBtn.setMaxWidth(160);
        confirmVerifyBtn.setOnAction(e -> confirmAccountEmailVerification());
        HBox verifyActions = new HBox(8, sendVerifyBtn, accountEmailVerifyCodeField, confirmVerifyBtn);
        verifyActions.setAlignment(Pos.CENTER_LEFT);

        Button toProfile = createGlowButton("Modifier le profil", "#2962FF");
        toProfile.setMaxWidth(260);
        toProfile.setOnAction(e -> showProfilePage());

        Separator sepDanger = new Separator();
        sepDanger.setPadding(new Insets(16, 0, 8, 0));
        Label dangerKicker = sectionKicker("Suppression du compte");
        Label dangerText =
                new Label(
                        "Action irréversible : effacement du profil, de toutes vos commandes et lignes, de"
                                + " l’historique de paiement et des moyens de paiement enregistrés (modèles masqués).");
        dangerText.setWrapText(true);
        dangerText.setTextFill(Color.web("#fca5a5"));
        dangerText.setFont(Font.font(11));

        deleteAccountPasswordField = new PasswordField();
        deleteAccountPasswordField.setPromptText("Mot de passe actuel");
        deleteAccountPasswordField.setStyle(inputStyle());
        deleteAccountOtpField = new TextField();
        deleteAccountOtpField.setPromptText("Code à 6 chiffres");
        deleteAccountOtpField.setStyle(inputStyle());
        Button sendDelOtpBtn = createOutlineButton("Recevoir un code");
        sendDelOtpBtn.setMaxWidth(220);
        sendDelOtpBtn.setOnAction(e -> sendProfileSecurityOtp());
        HBox delOtpRow = new HBox(8, deleteAccountOtpField, sendDelOtpBtn);
        delOtpRow.setAlignment(Pos.CENTER_LEFT);
        Label delOtpHint =
                new Label(
                        "Si votre e-mail est vérifié : demandez un code, puis saisissez-le (comme pour le profil).");
        delOtpHint.setWrapText(true);
        delOtpHint.setTextFill(Color.web("#94a3b8"));
        delOtpHint.setFont(Font.font(10));
        VBox delOtpBlock = new VBox(6, delOtpHint, delOtpRow);

        Button deleteAccBtn = createOutlineButton("Supprimer définitivement mon compte");
        deleteAccBtn.setTextFill(Color.web("#fecaca"));
        deleteAccBtn.setMaxWidth(340);
        deleteAccBtn.setOnAction(e -> confirmAndDeleteAccount());

        card.getChildren()
                .addAll(
                        hint,
                        lRole,
                        accountRoleBadgeLabel,
                        lName,
                        accountDispNameLabel,
                        lEmail,
                        accountEmailLabel,
                        lPhone,
                        accountPhoneLabel,
                        lVerify,
                        accountEmailVerifyStatusLabel,
                        verifyActions,
                        toProfile,
                        sepDanger,
                        dangerKicker,
                        dangerText);
        gridProfileRow(card, "Mot de passe pour confirmer", deleteAccountPasswordField);
        gridProfileRow(card, "Code de sécurité", delOtpBlock);
        card.getChildren().add(deleteAccBtn);
        scrollContent.getChildren().addAll(back, title, card);
        refreshAccountLabels();

        ScrollPane accountScroll = new ScrollPane(scrollContent);
        accountScroll.setFitToWidth(true);
        accountScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrap = new VBox(accountScroll);
        VBox.setVgrow(accountScroll, Priority.ALWAYS);
        return wrap;
    }

    private VBox buildCenterProfile() {
        VBox scrollContent = new VBox(18);
        scrollContent.setPadding(new Insets(0, 12, 0, 12));
        scrollContent.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour au compte");
        back.setOnAction(e -> showAccountPage());

        Label title = new Label("Modifier le profil");
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        title.setTextFill(Color.WHITE);

        VBox card = createGlassBox(16, 20);
        profilePasswordCurrent = new PasswordField();
        profilePasswordCurrent.setPromptText("Mot de passe actuel (obligatoire)");
        profilePasswordCurrent.setStyle(inputStyle());
        profilePasswordNew = new PasswordField();
        profilePasswordNew.setPromptText("Nouveau mot de passe (optionnel)");
        profilePasswordNew.setStyle(inputStyle());
        profileEmailField = new TextField();
        profileEmailField.setPromptText("E-mail");
        profileEmailField.setStyle(inputStyle());
        profilePhoneField = new TextField();
        profilePhoneField.setPromptText("Téléphone (chiffres)");
        profilePhoneField.setStyle(inputStyle());

        gridProfileRow(card, "Mot de passe actuel", profilePasswordCurrent);
        gridProfileRow(card, "Nouveau mot de passe", profilePasswordNew);
        gridProfileRow(card, "E-mail", profileEmailField);
        gridProfileRow(card, "Téléphone", profilePhoneField);

        Label secHint =
                new Label(
                        "Pour modifier le mot de passe, l’e-mail ou le numéro de téléphone : votre e-mail doit être"
                                + " vérifié (page Compte) et vous devez saisir un code de sécurité reçu par e-mail.");
        secHint.setWrapText(true);
        secHint.setTextFill(Color.web("#7A869F"));
        secHint.setFont(Font.font(10));
        profileSecurityOtpField = new TextField();
        profileSecurityOtpField.setPromptText("Code de sécurité à 6 chiffres");
        profileSecurityOtpField.setStyle(inputStyle());
        Button sendOtpBtn = createOutlineButton("Recevoir un code par e-mail");
        sendOtpBtn.setMaxWidth(320);
        sendOtpBtn.setOnAction(e -> sendProfileSecurityOtp());
        VBox secBox = new VBox(8, secHint, sendOtpBtn, profileSecurityOtpField);
        card.getChildren().add(secBox);

        Button save = createGlowButton("Enregistrer les modifications", "#18C964");
        save.setMaxWidth(320);
        save.setOnAction(e -> submitProfileUpdateFromPage());

        card.getChildren().add(save);
        scrollContent.getChildren().addAll(back, title, card);

        ScrollPane profileScroll = new ScrollPane(scrollContent);
        profileScroll.setFitToWidth(true);
        profileScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrap = new VBox(profileScroll);
        VBox.setVgrow(profileScroll, Priority.ALWAYS);
        return wrap;
    }

    private VBox buildCenterSeller() {
        VBox scrollContent = new VBox(18);
        scrollContent.setPadding(new Insets(0, 12, 0, 12));
        scrollContent.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour à l’accueil");
        back.setOnAction(e -> showAccueil());

        Label title = new Label("Espace vendeur");
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        title.setTextFill(Color.WHITE);

        Label hint =
                new Label(
                        "Proposez une fiche produit : elle reste « en attente » jusqu’à validation par un"
                                + " administrateur. Rôle requis : SELLER (voir scripts SQL).");
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#9DB0D4"));
        hint.setFont(Font.font(12));

        VBox form = createGlassBox(14, 16);
        sellerNomField = new TextField();
        sellerNomField.setPromptText("Nom du produit");
        sellerNomField.setStyle(inputStyle());
        sellerMarqueField = new TextField();
        sellerMarqueField.setPromptText("Marque (optionnel)");
        sellerMarqueField.setStyle(inputStyle());
        sellerCatField = new TextField();
        sellerCatField.setPromptText("Catégorie métier (ex. Électronique)");
        sellerCatField.setStyle(inputStyle());
        sellerPrixField = new TextField();
        sellerPrixField.setPromptText("Prix USD");
        sellerPrixField.setStyle(inputStyle());
        sellerStockField = new TextField();
        sellerStockField.setPromptText("Stock");
        sellerStockField.setStyle(inputStyle());
        sellerDescField = new TextField();
        sellerDescField.setPromptText("Description courte");
        sellerDescField.setStyle(inputStyle());
        sellerImgField = new TextField();
        sellerImgField.setPromptText("URL image (optionnel)");
        sellerImgField.setStyle(inputStyle());
        gridProfileRow(form, "Nom", sellerNomField);
        gridProfileRow(form, "Marque", sellerMarqueField);
        gridProfileRow(form, "Catégorie", sellerCatField);
        gridProfileRow(form, "Prix (USD)", sellerPrixField);
        gridProfileRow(form, "Stock", sellerStockField);
        gridProfileRow(form, "Description", sellerDescField);
        gridProfileRow(form, "Image URL", sellerImgField);

        Button submit = createGlowButton("Soumettre pour validation", "#18C964");
        submit.setMaxWidth(320);
        submit.setOnAction(e -> submitSellerListing());
        form.getChildren().add(submit);

        Label mineTitle = new Label("Mes fiches");
        mineTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        mineTitle.setTextFill(Color.WHITE);
        sellerMyListingsBox = new VBox(8);
        sellerMyListingsBox.setFillWidth(true);

        scrollContent.getChildren().addAll(back, title, hint, form, mineTitle, sellerMyListingsBox);
        ScrollPane sp = new ScrollPane(scrollContent);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrap = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return wrap;
    }

    private VBox buildCenterModeration() {
        VBox scrollContent = new VBox(18);
        scrollContent.setPadding(new Insets(0, 12, 0, 12));
        scrollContent.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour à l’accueil");
        back.setOnAction(e -> showAccueil());

        Label title = new Label("Modération catalogue");
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 24));
        title.setTextFill(Color.WHITE);

        Label hint =
                new Label(
                        "Validez ou refusez les fiches soumises par les vendeurs. Rôle requis : ADMIN (voir scripts SQL).");
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#9DB0D4"));
        hint.setFont(Font.font(12));

        Button refresh = createOutlineButton("Actualiser la file d’attente");
        refresh.setMaxWidth(280);
        refresh.setOnAction(e -> refreshAdminPendingModeration());

        moderationPendingBox = new VBox(10);
        moderationPendingBox.setFillWidth(true);

        scrollContent.getChildren().addAll(back, title, hint, refresh, moderationPendingBox);
        ScrollPane sp = new ScrollPane(scrollContent);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrap = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return wrap;
    }

    /** UX séparée de la modération : fond ambré, formulaire création directe, tableau suppressions. */
    private VBox buildCenterAdminCatalog() {
        VBox scrollContent = new VBox(18);
        scrollContent.setPadding(new Insets(0, 12, 0, 12));
        scrollContent.setMaxWidth(Double.MAX_VALUE);

        Button back = createOutlineButton("← Retour à l’accueil");
        back.setOnAction(e -> showAccueil());

        Label title = new Label("Console catalogue — administrateur");
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 26));
        title.setTextFill(Color.web("#FBBF24"));

        Label subtitle =
                new Label(
                        "Ajoutez des produits publiés immédiatement ou supprimez des fiches existantes. "
                                + "Rôle ADMIN requis.");
        subtitle.setWrapText(true);
        subtitle.setTextFill(Color.web("#FDE68A"));
        subtitle.setFont(Font.font(12));

        VBox chrome = new VBox(18);
        chrome.setPadding(new Insets(18));
        chrome.setStyle(
                "-fx-background-color: rgba(245,158,11,0.08); -fx-background-radius: 18; "
                        + "-fx-border-color: rgba(251,191,36,0.55); -fx-border-radius: 18; -fx-border-width: 1.5;");

        Label formTitle = new Label("Nouveau produit (catalogue direct)");
        formTitle.setFont(Font.font("Arial", FontWeight.BOLD, 17));
        formTitle.setTextFill(Color.web("#FFFBEB"));

        VBox formInner = createGlassBox(12, 14);
        adminProdNomField = new TextField();
        adminProdNomField.setPromptText("Nom du produit");
        adminProdNomField.setStyle(inputStyle());
        adminProdSkuField = new TextField();
        adminProdSkuField.setPromptText("SKU optionnel — laissez vide pour génération automatique");
        adminProdSkuField.setStyle(inputStyle());
        adminProdMarqueField = new TextField();
        adminProdMarqueField.setPromptText("Marque");
        adminProdMarqueField.setStyle(inputStyle());
        adminProdCatCombo = new ComboBox<>();
        adminProdCatCombo.setPromptText("Choisir une catégorie");
        adminProdCatCombo.setEditable(false);
        adminProdCatCombo.setMaxWidth(Double.MAX_VALUE);
        adminProdCatCombo.setStyle(inputStyle());
        adminProdCatCombo.setItems(
                FXCollections.observableArrayList(
                        "Accessoires sport",
                        "Electronique",
                        "Maison & decoration",
                        "Produits de bain & soin",
                        "Vêtements & mode"));
        adminProdCatCombo.getSelectionModel().selectFirst();
        adminProdPrixField = new TextField();
        adminProdPrixField.setPromptText("Prix USD");
        adminProdPrixField.setStyle(inputStyle());
        adminProdStockField = new TextField();
        adminProdStockField.setPromptText("Stock");
        adminProdStockField.setStyle(inputStyle());
        adminProdDescField = new TextField();
        adminProdDescField.setPromptText("Description");
        adminProdDescField.setStyle(inputStyle());
        adminProdImgField = new TextField();
        adminProdImgField.setPromptText("URL image optionnelle — http:// ou https://");
        adminProdImgField.setStyle(inputStyle());

        gridProfileRow(formInner, "Nom", adminProdNomField);
        gridProfileRow(formInner, "SKU", adminProdSkuField);
        gridProfileRow(formInner, "Marque", adminProdMarqueField);
        gridProfileRow(formInner, "Catégorie", adminProdCatCombo);
        gridProfileRow(formInner, "Prix (USD)", adminProdPrixField);
        gridProfileRow(formInner, "Stock", adminProdStockField);
        gridProfileRow(formInner, "Description", adminProdDescField);
        gridProfileRow(formInner, "Image URL", adminProdImgField);

        Button createBtn = createGlowButton("Créer et publier", "#D97706");
        createBtn.setMaxWidth(340);
        createBtn.setOnAction(e -> submitAdminCatalogCreate());
        formInner.getChildren().add(createBtn);

        Label listTitle = new Label("Inventaire (aperçu — tous statuts)");
        listTitle.setFont(Font.font("Arial", FontWeight.BOLD, 17));
        listTitle.setTextFill(Color.web("#FFFBEB"));

        adminProductSearchField = new TextField();
        adminProductSearchField.setPromptText("Rechercher par ID, nom, SKU, marque ou catégorie");
        adminProductSearchField.setStyle(inputStyle());
        adminProductSearchField.setOnAction(e -> refreshAdminCatalogList());

        Button refreshList = createOutlineButton("Actualiser / rechercher");
        refreshList.setMaxWidth(260);
        refreshList.setOnAction(e -> refreshAdminCatalogList());

        adminCatalogRowsBox = new VBox(10);
        adminCatalogRowsBox.setFillWidth(true);

        chrome.getChildren().addAll(formTitle, formInner, listTitle, adminProductSearchField, refreshList, adminCatalogRowsBox);
        scrollContent.getChildren().addAll(back, title, subtitle, chrome);

        ScrollPane sp = new ScrollPane(scrollContent);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox wrap = new VBox(sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        return wrap;
    }

    private void showSellerPage() {
        showOnlyCenter(centerSeller);
        clearNavHighlight();
        refreshSellerMyListings();
    }

    private void showAdminModerationPage() {
        showOnlyCenter(centerModeration);
        clearNavHighlight();
        refreshAdminPendingModeration();
    }

    private void showAdminCatalogPage() {
        if (!"ADMIN".equalsIgnoreCase(sessionRole != null ? sessionRole.trim() : "")) {
            alert(Alert.AlertType.WARNING, "Accès réservé aux comptes administrateur.");
            return;
        }
        showOnlyCenter(centerAdminCatalog);
        clearNavHighlight();
        refreshAdminProductCategories();
        refreshAdminCatalogList();
    }

    private void submitSellerListing() {
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            alert(Alert.AlertType.WARNING, "Connectez-vous d’abord.");
            return;
        }
        if (!"SELLER".equalsIgnoreCase(sessionRole)) {
            alert(Alert.AlertType.WARNING, "Compte vendeur requis (rôle SELLER en base).");
            return;
        }
        if (host == null || host.isBlank()) {
            alert(Alert.AlertType.WARNING, "Connectez-vous au serveur.");
            return;
        }
        String nom = sellerNomField != null ? sellerNomField.getText().trim() : "";
        if (nom.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Indiquez un nom de produit.");
            return;
        }
        String json =
                "{\"sessionToken\":\""
                        + jsonEsc(sessionToken)
                        + "\",\"nomProduit\":\""
                        + jsonEsc(nom)
                        + "\",\"marque\":\""
                        + jsonEsc(sellerMarqueField != null ? sellerMarqueField.getText().trim() : "")
                        + "\",\"categorieMetier\":\""
                        + jsonEsc(sellerCatField != null ? sellerCatField.getText().trim() : "Général")
                        + "\",\"prixUsd\":\""
                        + jsonEsc(sellerPrixField != null ? sellerPrixField.getText().trim() : "0")
                        + "\",\"stock\":\""
                        + jsonEsc(sellerStockField != null ? sellerStockField.getText().trim() : "0")
                        + "\",\"description\":\""
                        + jsonEsc(sellerDescField != null ? sellerDescField.getText().trim() : "")
                        + "\",\"imageUrl\":\""
                        + jsonEsc(sellerImgField != null ? sellerImgField.getText().trim() : "")
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("SUBMIT_PRODUCT_LISTING", "spl", json));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        showAppToast(
                                                "Fiche soumise. En attente de validation par un administrateur.",
                                                Alert.AlertType.INFORMATION);
                                        refreshSellerMyListings();
                                    } else {
                                        alertAdminProductCreateFailure(res);
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void refreshSellerMyListings() {
        if (sellerMyListingsBox == null) {
            return;
        }
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            sellerMyListingsBox.getChildren().clear();
            sellerMyListingsBox.getChildren().add(new Label("Connectez-vous pour voir vos fiches."));
            return;
        }
        if (host == null || host.isBlank()) {
            sellerMyListingsBox.getChildren().clear();
            sellerMyListingsBox.getChildren().add(new Label("Connectez-vous au serveur."));
            return;
        }
        String payload = "{\"sessionToken\":\"" + jsonEsc(sessionToken) + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("LIST_MY_PRODUCT_LISTINGS", "myl", payload));
                        Platform.runLater(
                                () -> {
                                    sellerMyListingsBox.getChildren().clear();
                                    if (!"SUCCESS".equals(res.getStatus())) {
                                        Label err = new Label(UiMessages.errorCode(res.getErrorCode()));
                                        err.setTextFill(Color.web("#F87171"));
                                        err.setWrapText(true);
                                        sellerMyListingsBox.getChildren().add(err);
                                        return;
                                    }
                                    for (SocketApiClient.PendingListingRow row :
                                            SocketApiClient.parsePendingListingRows(res.getPayload())) {
                                        Label line =
                                                new Label(
                                                        "N°"
                                                                + row.productId()
                                                                + " · "
                                                                + row.nomProduit()
                                                                + " · "
                                                                + row.listingStatus()
                                                                + (row.rejectionReason() != null
                                                                                && !row.rejectionReason().isEmpty()
                                                                        ? " — "
                                                                                + row.rejectionReason()
                                                                        : ""));
                                        line.setTextFill(Color.web("#E2E8F0"));
                                        line.setWrapText(true);
                                        sellerMyListingsBox.getChildren().add(line);
                                    }
                                    if (sellerMyListingsBox.getChildren().isEmpty()) {
                                        sellerMyListingsBox
                                                .getChildren()
                                                .add(new Label("Aucune fiche pour le moment."));
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void refreshAdminPendingModeration() {
        if (moderationPendingBox == null) {
            return;
        }
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            moderationPendingBox.getChildren().clear();
            moderationPendingBox.getChildren().add(new Label("Connectez-vous pour modérer."));
            return;
        }
        if (host == null || host.isBlank()) {
            moderationPendingBox.getChildren().clear();
            moderationPendingBox.getChildren().add(new Label("Connectez-vous au serveur."));
            return;
        }
        String payload = "{\"sessionToken\":\"" + jsonEsc(sessionToken) + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("LIST_PENDING_PRODUCTS", "lpp", payload));
                        Platform.runLater(
                                () -> {
                                    moderationPendingBox.getChildren().clear();
                                    if (!"SUCCESS".equals(res.getStatus())) {
                                        Label err = new Label(UiMessages.errorCode(res.getErrorCode()));
                                        err.setTextFill(Color.web("#F87171"));
                                        err.setWrapText(true);
                                        moderationPendingBox.getChildren().add(err);
                                        return;
                                    }
                                    List<SocketApiClient.PendingListingRow> rows =
                                            SocketApiClient.parsePendingListingRows(res.getPayload());
                                    if (rows.isEmpty()) {
                                        moderationPendingBox
                                                .getChildren()
                                                .add(new Label("Aucune fiche en attente."));
                                        return;
                                    }
                                    for (SocketApiClient.PendingListingRow row : rows) {
                                        HBox rowBox = new HBox(10);
                                        rowBox.setAlignment(Pos.CENTER_LEFT);
                                        Label lbl =
                                                new Label(
                                                        "N°"
                                                                + row.productId()
                                                                + " · "
                                                                + row.nomProduit()
                                                                + " (vendeur #"
                                                                + row.sellerId()
                                                                + ")");
                                        lbl.setTextFill(Color.web("#E2E8F0"));
                                        lbl.setWrapText(true);
                                        HBox.setHgrow(lbl, Priority.ALWAYS);
                                        Button ok = createGlowButton("Approuver", "#18C964");
                                        ok.setOnAction(e -> approveOrRejectListing(row.productId(), true));
                                        Button ko = createOutlineButton("Refuser");
                                        ko.setOnAction(e -> approveOrRejectListing(row.productId(), false));
                                        rowBox.getChildren().addAll(lbl, ok, ko);
                                        moderationPendingBox.getChildren().add(rowBox);
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void refreshAdminProductCategories() {
        if (adminProdCatCombo == null || !accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            return;
        }
        String current = adminProdCatCombo.getValue();
        String payload = "{\"sessionToken\":\"" + jsonEsc(sessionToken) + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("ADMIN_PRODUCT_CATEGORIES", "apcat", payload));
                        if (!"SUCCESS".equals(res.getStatus())) {
                            return;
                        }
                        List<String> categories = SocketApiClient.parseStringArray(res.getPayload());
                        Platform.runLater(
                                () -> {
                                    if (adminProdCatCombo == null || categories.isEmpty()) {
                                        return;
                                    }
                                    adminProdCatCombo.setItems(FXCollections.observableArrayList(categories));
                                    if (current != null && categories.contains(current)) {
                                        adminProdCatCombo.getSelectionModel().select(current);
                                    } else {
                                        adminProdCatCombo.getSelectionModel().selectFirst();
                                    }
                                });
                    } catch (Exception ignored) {
                        // La liste locale par défaut reste utilisable si le serveur est temporairement indisponible.
                    }
                });
    }

    private void refreshAdminCatalogList() {
        if (adminCatalogRowsBox == null) {
            return;
        }
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            adminCatalogRowsBox.getChildren().clear();
            adminCatalogRowsBox.getChildren().add(new Label("Connectez-vous avec un compte ADMIN."));
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(sessionRole != null ? sessionRole.trim() : "")) {
            adminCatalogRowsBox.getChildren().clear();
            adminCatalogRowsBox.getChildren().add(new Label("Rôle administrateur requis."));
            return;
        }
        if (host == null || host.isBlank()) {
            adminCatalogRowsBox.getChildren().clear();
            adminCatalogRowsBox.getChildren().add(new Label("Connectez-vous au serveur."));
            return;
        }
        String search = adminProductSearchField != null ? adminProductSearchField.getText().trim() : "";
        String payload =
                "{\"sessionToken\":\""
                        + jsonEsc(sessionToken)
                        + "\",\"search\":\""
                        + jsonEsc(search)
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("ADMIN_PRODUCT_LIST", "apl2", payload));
                        Platform.runLater(
                                () -> {
                                    adminCatalogRowsBox.getChildren().clear();
                                    if (!"SUCCESS".equals(res.getStatus())) {
                                        Label err = new Label(UiMessages.errorCode(res.getErrorCode()));
                                        err.setTextFill(Color.web("#F87171"));
                                        err.setWrapText(true);
                                        adminCatalogRowsBox.getChildren().add(err);
                                        return;
                                    }
                                    List<SocketApiClient.AdminCatalogRow> rows =
                                            SocketApiClient.parseAdminCatalogRows(res.getPayload());
                                    if (rows.isEmpty()) {
                                        Label empty =
                                                new Label(
                                                        search.isBlank()
                                                                ? "Aucun produit en base."
                                                                : "Aucun produit ne correspond à cette recherche.");
                                        empty.setTextFill(Color.web("#CBD5E1"));
                                        adminCatalogRowsBox.getChildren().add(empty);
                                        return;
                                    }
                                    for (SocketApiClient.AdminCatalogRow row : rows) {
                                        VBox rowCard = new VBox(8);
                                        rowCard.setPadding(new Insets(10, 12, 10, 12));
                                        rowCard.setStyle(
                                                "-fx-background-color: rgba(15,23,42,0.65); -fx-background-radius: 12; "
                                                        + "-fx-border-color: rgba(251,191,36,0.25); -fx-border-radius: 12;");
                                        Label line1 =
                                                new Label(
                                                        "#"
                                                                + row.productId()
                                                                + " · "
                                                                + row.nom()
                                                                + " · "
                                                                + String.format(
                                                                        Locale.FRENCH, "%.2f USD", row.prixUsd()));
                                        line1.setTextFill(Color.web("#FEF3C7"));
                                        line1.setFont(Font.font("Arial", FontWeight.BOLD, 13));
                                        line1.setWrapText(true);
                                        Label line2 =
                                                new Label(
                                                        "SKU "
                                                                + row.sku()
                                                                + " · Marque "
                                                                + (row.marque().isBlank() ? "—" : row.marque())
                                                                + " · Catégorie "
                                                                + (row.categorieMetier().isBlank()
                                                                        ? "—"
                                                                        : row.categorieMetier()));
                                        line2.setTextFill(Color.web("#CBD5E1"));
                                        line2.setFont(Font.font(11));
                                        line2.setWrapText(true);
                                        Label line3 =
                                                new Label(
                                                        "Stock "
                                                                + row.stock()
                                                                + " · Statut "
                                                                + row.listingStatus());
                                        line3.setTextFill(Color.web(row.stock() > 0 ? "#BBF7D0" : "#FCA5A5"));
                                        line3.setFont(Font.font(11));
                                        line3.setWrapText(true);

                                        HBox actions = new HBox(8);
                                        actions.setAlignment(Pos.CENTER_LEFT);
                                        Button stockBtn = createOutlineButton("Modifier stock");
                                        stockBtn.setOnAction(
                                                e ->
                                                        requestAdminStockUpdate(
                                                                row.productId(), row.stock(), row.nom()));
                                        Button del = createOutlineButton("Supprimer");
                                        del.setTextFill(Color.web("#fecaca"));
                                        del.setOnAction(
                                                e ->
                                                        confirmDeleteAdminProduct(
                                                                row.productId(), row.nom()));
                                        actions.getChildren().addAll(stockBtn, del);

                                        rowCard.getChildren().addAll(line1, line2, line3, actions);
                                        adminCatalogRowsBox.getChildren().add(rowCard);
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void requestAdminStockUpdate(int productId, int oldStock, String nom) {
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            alert(Alert.AlertType.WARNING, "Connectez-vous d’abord.");
            return;
        }
        TextInputDialog dlg = new TextInputDialog(String.valueOf(oldStock));
        dlg.setTitle("Modifier le stock");
        dlg.setHeaderText("Produit #" + productId + " — " + nom);
        dlg.setContentText("Nouveau stock :");
        Optional<String> answer = dlg.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        int newStock;
        try {
            newStock = Integer.parseInt(answer.get().trim());
        } catch (NumberFormatException e) {
            alert(Alert.AlertType.WARNING, "Le stock doit être un nombre entier.");
            return;
        }
        if (newStock < 0) {
            alert(Alert.AlertType.WARNING, "Le stock ne peut pas être négatif.");
            return;
        }
        String json =
                "{\"sessionToken\":\""
                        + jsonEsc(sessionToken)
                        + "\",\"productId\":\""
                        + productId
                        + "\",\"stock\":\""
                        + newStock
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("ADMIN_PRODUCT_UPDATE_STOCK", "apus", json));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        refreshAdminCatalogList();
                                        loadCatalogForCategory(
                                                currentCatalogCategory != null
                                                        ? currentCatalogCategory
                                                        : "Tous");
                                        updateDashboardStats();
                                        showAppToast("Stock mis à jour.", Alert.AlertType.INFORMATION);
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void confirmDeleteAdminProduct(int productId, String nom) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le produit");
        confirm.setHeaderText("Produit #" + productId);
        confirm.setContentText(
                "Confirmer la suppression définitive de « " + nom + " » ? Cette action retire la fiche du catalogue.");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }
        String json =
                "{\"sessionToken\":\""
                        + jsonEsc(sessionToken)
                        + "\",\"productId\":\""
                        + productId
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("ADMIN_PRODUCT_DELETE", "apd", json));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        refreshAdminCatalogList();
                                        loadCategoryList();
                                        loadCatalogForCategory(
                                                currentCatalogCategory != null
                                                        ? currentCatalogCategory
                                                        : "Tous");
                                        showAppToast("Produit supprimé.", Alert.AlertType.INFORMATION);
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void submitAdminCatalogCreate() {
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            alert(Alert.AlertType.WARNING, "Connectez-vous d’abord.");
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(sessionRole != null ? sessionRole.trim() : "")) {
            alert(Alert.AlertType.WARNING, "Compte administrateur requis.");
            return;
        }
        if (host == null || host.isBlank()) {
            alert(Alert.AlertType.WARNING, "Connectez-vous au serveur.");
            return;
        }

        String nom = fieldText(adminProdNomField);
        String sku = fieldText(adminProdSkuField);
        String marque = fieldText(adminProdMarqueField);
        String categorie = adminProdCatCombo != null ? adminProdCatCombo.getValue() : "";
        String prixText = fieldText(adminProdPrixField).replace(',', '.');
        String stockText = fieldText(adminProdStockField);
        String description = fieldText(adminProdDescField);
        String imageUrl = fieldText(adminProdImgField);

        String validation = validateAdminProductForm(nom, sku, marque, categorie, prixText, stockText, imageUrl);
        if (validation != null) {
            alert(Alert.AlertType.WARNING, validation);
            return;
        }

        StringBuilder json =
                new StringBuilder()
                        .append("{\"sessionToken\":\"")
                        .append(jsonEsc(sessionToken))
                        .append("\",\"nomProduit\":\"")
                        .append(jsonEsc(nom))
                        .append("\"");
        if (!sku.isBlank()) {
            json.append(",\"sku\":\"").append(jsonEsc(sku)).append("\"");
        }
        json.append(",\"marque\":\"")
                .append(jsonEsc(marque))
                .append("\",\"categorieMetier\":\"")
                .append(jsonEsc(categorie.trim()))
                .append("\",\"prixUsd\":\"")
                .append(jsonEsc(prixText))
                .append("\",\"stock\":\"")
                .append(jsonEsc(stockText))
                .append("\",\"description\":\"")
                .append(jsonEsc(description))
                .append("\",\"imageUrl\":\"")
                .append(jsonEsc(imageUrl))
                .append("\"}");

        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("ADMIN_PRODUCT_CREATE", "apc", json.toString()));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        clearAdminProductForm();
                                        refreshAdminProductCategories();
                                        refreshAdminCatalogList();
                                        loadCategoryList();
                                        loadCatalogForCategory(
                                                currentCatalogCategory != null
                                                        ? currentCatalogCategory
                                                        : "Tous");
                                        updateDashboardStats();
                                        showAppToast("Produit créé et publié.", Alert.AlertType.INFORMATION);
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private String fieldText(TextInputControl field) {
        return field != null && field.getText() != null ? field.getText().trim() : "";
    }

    private String validateAdminProductForm(
            String nom,
            String sku,
            String marque,
            String categorie,
            String prixText,
            String stockText,
            String imageUrl) {
        if (nom.isBlank()) {
            return "Indiquez un nom de produit.";
        }
        if (nom.length() > 255) {
            return "Le nom du produit est trop long (maximum 255 caractères).";
        }
        if (!sku.isBlank()) {
            if (sku.length() > 40) {
                return "Le SKU est trop long (maximum 40 caractères).";
            }
            if (!sku.matches("[A-Za-z0-9._-]+")) {
                return "SKU invalide : utilisez seulement lettres, chiffres, tiret, underscore ou point.";
            }
            if ("ADMIN-TMP".equalsIgnoreCase(sku) || "SELL-TMP".equalsIgnoreCase(sku)) {
                return "N’utilisez pas ADMIN-TMP / SELL-TMP comme SKU. Laissez le champ vide pour générer un SKU.";
            }
        }
        if (marque.length() > 120) {
            return "La marque est trop longue (maximum 120 caractères).";
        }
        if (categorie == null || categorie.isBlank()) {
            return "Choisissez une catégorie dans la liste.";
        }
        if (categorie.length() > 120) {
            return "La catégorie est trop longue (maximum 120 caractères).";
        }
        try {
            double prix = Double.parseDouble(prixText);
            if (prix <= 0) {
                return "Le prix doit être supérieur à zéro.";
            }
        } catch (NumberFormatException e) {
            return "Le prix doit être un nombre valide, par exemple 350 ou 350.99.";
        }
        try {
            int stock = Integer.parseInt(stockText);
            if (stock < 0) {
                return "Le stock ne peut pas être négatif.";
            }
        } catch (NumberFormatException e) {
            return "Le stock doit être un nombre entier, par exemple 4.";
        }
        if (!imageUrl.isBlank()) {
            if (imageUrl.length() > 768) {
                return "L’URL image est trop longue (maximum 768 caractères).";
            }
            String lower = imageUrl.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                return "L’URL image doit commencer par http:// ou https://, sinon laissez le champ vide.";
            }
        }
        return null;
    }

    private void clearAdminProductForm() {
        if (adminProdNomField != null) {
            adminProdNomField.clear();
        }
        if (adminProdSkuField != null) {
            adminProdSkuField.clear();
        }
        if (adminProdMarqueField != null) {
            adminProdMarqueField.clear();
        }
        if (adminProdCatCombo != null) {
            adminProdCatCombo.getSelectionModel().selectFirst();
        }
        if (adminProdPrixField != null) {
            adminProdPrixField.clear();
        }
        if (adminProdStockField != null) {
            adminProdStockField.clear();
        }
        if (adminProdDescField != null) {
            adminProdDescField.clear();
        }
        if (adminProdImgField != null) {
            adminProdImgField.clear();
        }
    }

    private void approveOrRejectListing(int productId, boolean approve) {
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            return;
        }
        if (!approve) {
            TextInputDialog dlg = new TextInputDialog();
            dlg.setTitle("Motif de refus");
            dlg.setHeaderText("Optionnel : motif affiché au vendeur");
            dlg.showAndWait();
            String reason = dlg.getEditor().getText().trim();
            String json =
                    "{\"sessionToken\":\""
                            + jsonEsc(sessionToken)
                            + "\",\"productId\":\""
                            + productId
                            + "\",\"reason\":\""
                            + jsonEsc(reason)
                            + "\"}";
            runAsync(
                    () -> {
                        try {
                            Message res =
                                    api.send(Message.request("REJECT_PRODUCT_LISTING", "rpl", json));
                            Platform.runLater(
                                    () -> {
                                        if ("SUCCESS".equals(res.getStatus())) {
                                            refreshAdminPendingModeration();
                                        } else {
                                            alertError(res.getErrorCode());
                                        }
                                    });
                        } catch (Exception ex) {
                            Platform.runLater(() -> alertThrowable(ex));
                        }
                    });
            return;
        }
        String json =
                "{\"sessionToken\":\""
                        + jsonEsc(sessionToken)
                        + "\",\"productId\":\""
                        + productId
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("APPROVE_PRODUCT_LISTING", "apl", json));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        refreshAdminPendingModeration();
                                        loadCategoryList();
                                        loadCatalogForCategory(
                                                currentCatalogCategory != null
                                                        ? currentCatalogCategory
                                                        : "Tous");
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private Label sectionKicker(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#7183A8"));
        l.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        return l;
    }

    private void gridProfileRow(VBox card, String label, javafx.scene.Node field) {
        VBox rowBox = new VBox(6);
        Label l = new Label(label);
        l.setTextFill(Color.web("#A8B4D1"));
        rowBox.getChildren().addAll(l, field);
        card.getChildren().add(rowBox);
    }

    private void confirmAndDeleteAccount() {
        if (!accountLoggedIn || userId <= 0) {
            alert(Alert.AlertType.WARNING, "Connectez-vous d’abord.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le compte");
        confirm.setHeaderText("Suppression définitive");
        confirm.setContentText(
                "Toutes vos données de compte, commandes et moyens de paiement enregistrés seront effacées."
                        + " Continuer ?");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }
        String pw = deleteAccountPasswordField != null ? deleteAccountPasswordField.getText() : "";
        if (pw.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Saisissez votre mot de passe actuel.");
            return;
        }
        if (sessionEmailVerified) {
            String otp = deleteAccountOtpField != null ? deleteAccountOtpField.getText().trim() : "";
            if (otp.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Demandez un code par e-mail, puis saisissez-le.");
                return;
            }
        }
        StringBuilder json =
                new StringBuilder()
                        .append("{\"userId\":\"")
                        .append(userId)
                        .append("\",\"currentPassword\":\"")
                        .append(jsonEsc(pw))
                        .append("\"");
        if (sessionEmailVerified && deleteAccountOtpField != null) {
            json.append(",\"securityOtp\":\"").append(jsonEsc(deleteAccountOtpField.getText().trim())).append("\"");
        }
        json.append("}");
        String payload = json.toString();
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("DELETE_ACCOUNT", "delacct", payload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        if (deleteAccountPasswordField != null) {
                                            deleteAccountPasswordField.clear();
                                        }
                                        if (deleteAccountOtpField != null) {
                                            deleteAccountOtpField.clear();
                                        }
                                        applyLocalUserDataAfterAccountDeletion();
                                        alert(
                                                Alert.AlertType.INFORMATION,
                                                "Votre compte et vos données associées ont été supprimés.");
                                        showAccueil();
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    /** After server-side account removal: clear local session, cart, wishlist, order cache. */
    private void applyLocalUserDataAfterAccountDeletion() {
        cart.clear();
        wishlist.clear();
        commandesDetailCache.clear();
        lastCreatedCommandeId = null;
        updateCartSummary();
        updateFavoritesUI();
        updateDashboardStats();
        if (ordersCardsContainer != null) {
            rebuildOrdersCards();
        }
        refreshProductGrid();
        refreshTopSellers();
        refreshSavedPaymentMethods();
        logoutAccount();
    }

    private void submitProfileUpdateFromPage() {
        String cur = profilePasswordCurrent.getText() != null ? profilePasswordCurrent.getText() : "";
        if (cur.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Le mot de passe actuel est obligatoire.");
            return;
        }
        String npw = profilePasswordNew.getText() != null ? profilePasswordNew.getText().trim() : "";
        String ne = profileEmailField.getText() != null ? profileEmailField.getText().trim() : "";
        String nph = profilePhoneField.getText() != null ? profilePhoneField.getText().trim() : "";
        boolean changePw = !npw.isEmpty();
        boolean changeEmail = !ne.isEmpty() && !ne.equals(sessionEmail);
        boolean changePhone =
                !nph.isEmpty() && !nph.replaceAll("\\D+", "").equals(sessionPhone.replaceAll("\\D+", ""));
        if (!changePw && !changeEmail && !changePhone) {
            alert(Alert.AlertType.INFORMATION, "Aucune modification.");
            return;
        }
        boolean sensitive = changePw || changeEmail || changePhone;
        if (sensitive) {
            if (!sessionEmailVerified) {
                alert(
                        Alert.AlertType.WARNING,
                        "Vérifiez votre adresse e-mail depuis la page Compte avant de modifier ces informations.");
                return;
            }
            String otp = profileSecurityOtpField != null ? profileSecurityOtpField.getText().trim() : "";
            if (otp.isEmpty()) {
                alert(
                        Alert.AlertType.WARNING,
                        "Cliquez sur « Recevoir un code par e-mail », puis saisissez le code reçu.");
                return;
            }
        }
        StringBuilder json =
                new StringBuilder()
                        .append("{\"userId\":\"")
                        .append(userId)
                        .append("\",\"currentPassword\":\"")
                        .append(jsonEsc(cur))
                        .append("\"");
        if (!npw.isEmpty()) {
            json.append(",\"newPassword\":\"").append(jsonEsc(npw)).append("\"");
        }
        if (!ne.isEmpty() && !ne.equals(sessionEmail)) {
            json.append(",\"newEmail\":\"").append(jsonEsc(ne)).append("\"");
        }
        if (!nph.isEmpty() && !nph.replaceAll("\\D+", "").equals(sessionPhone.replaceAll("\\D+", ""))) {
            json.append(",\"newPhone\":\"").append(jsonEsc(nph.replaceAll("\\D+", ""))).append("\"");
        }
        if (sensitive) {
            String otp = profileSecurityOtpField.getText().trim();
            json.append(",\"securityOtp\":\"").append(jsonEsc(otp)).append("\"");
        }
        json.append("}");
        String payload = json.toString();
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("UPDATE_PROFILE", "22", payload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        applySession(res.getPayload());
                                        refreshAccountLabels();
                                        alert(Alert.AlertType.INFORMATION, "Profil mis à jour.");
                                        showAccountPage();
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private Label themedDetailLabel(String text, double size, FontWeight weight) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#E2E8F0"));
        l.setFont(Font.font("Arial", weight, size));
        l.setWrapText(true);
        return l;
    }

    private void styleSpinner(Spinner<Integer> sp) {
        sp.setStyle(
                "-fx-background-color: rgba(255,255,255,0.06);"
                        + "-fx-border-color: rgba(255,255,255,0.12);"
                        + "-fx-border-radius: 10;"
                        + "-fx-background-radius: 10;");
        sp.getEditor()
                .setStyle(
                        "-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.2); "
                                + "-fx-control-inner-background: rgba(0,0,0,0.2);");
    }

    private void refreshProductDetailView() {
        Product p = currentDetailProduct;
        if (p == null) {
            return;
        }
        productDetailTitle.setText(p.getName());
        productDetailPrice.setText(String.format(Locale.US, "%.2f USD", p.getPrice()));
        productDetailStock.setText("Stock disponible : " + p.getStock());
        productDetailCategory.setText(
                "Catégorie : " + (p.getCategory() != null && !p.getCategory().isEmpty() ? p.getCategory() : "—"));
        productDetailBrand.setText(
                "Marque : " + (p.getBrand() != null && !p.getBrand().isEmpty() ? p.getBrand() : "—"));
        productDetailRating.setText(
                p.getRating() > 0
                        ? String.format(Locale.US, "Note : ★ %.2f", p.getRating())
                        : "Note : —");
        productDetailDesc.setText(p.getDescription() != null ? p.getDescription() : "");
        int inCart = cart.getOrDefault(p.getId(), 0);
        productDetailInCart.setText("Dans le panier : " + inCart + " article(s)");
        int max = Math.max(1, p.getStock());
        int prev = 1;
        try {
            Integer v = productDetailQty.getValue();
            if (v != null) {
                prev = v;
            }
        } catch (Exception ignored) {
        }
        int start = Math.min(max, Math.max(1, prev));
        productDetailQty.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, start));
        productDetailFavBtn.setText(wishlist.contains(p.getId()) ? "Retirer des favoris" : "Ajouter aux favoris");

        productDetailImage.setImage(null);
        String url = p.getImageUrl();
        if (url != null && !url.isBlank()) {
            ProductImageLoader.loadAsync(
                    url,
                    420,
                    280,
                    img -> productDetailImage.setImage(img),
                    () -> productDetailImage.setImage(null));
        }
    }

    private void addProductDetailToCart() {
        if (currentDetailProduct == null) {
            return;
        }
        int add = productDetailQty.getValue() != null ? productDetailQty.getValue() : 1;
        int max = currentDetailProduct.getStock();
        int cur = cart.getOrDefault(currentDetailProduct.getId(), 0);
        if (add < 1) {
            return;
        }
        if (cur + add > max) {
            alert(Alert.AlertType.WARNING, "Stock insuffisant pour cette quantité.");
            return;
        }
        cart.put(currentDetailProduct.getId(), cur + add);
        updateCartSummary();
        refreshProductDetailView();
        refreshProductGrid();
        refreshTopSellers();
        updateDashboardStats();
    }

    private VBox buildCenterHome() {
        VBox inner = new VBox(14);
        inner.setPadding(new Insets(0, 12, 0, 12));

        HBox topBar = buildTopBar();
        VBox hero = buildHero();
        homeHeroSection = hero;
        VBox topSellerSection = buildTopSellerSection();
        homeTopSellerSection = topSellerSection;
        VBox promoSection = buildProductsSection();

        inner.getChildren().addAll(topBar, hero, topSellerSection, promoSection);

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setPannable(false);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        homeScrollPane = scroll;
        scroll.vvalueProperty()
                .addListener(
                        (obs, oldV, newV) -> {
                            if (newV == null) {
                                return;
                            }
                            double v = newV.doubleValue();
                            if (v > 0.82 && catalogHasMore && !catalogLoading) {
                                loadMoreCatalog();
                            }
                        });

        VBox wrap = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrap;
    }

    private VBox buildTopSellerSection() {
        VBox section = createGlassBox(12, 16);
        Label title = new Label("Top Sellers");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        topSellerSubtitleLabel =
                new Label("Produits populaires (notes, stock) — chargez le catalogue pour remplir cette grille.");
        topSellerSubtitleLabel.setTextFill(Color.web("#9DB0D4"));
        topSellerSubtitleLabel.setWrapText(true);
        topSellerGrid = new TilePane();
        topSellerGrid.setHgap(12);
        topSellerGrid.setVgap(12);
        topSellerGrid.setPrefColumns(3);
        section.getChildren().addAll(title, topSellerSubtitleLabel, topSellerGrid);
        return section;
    }

    private VBox buildCenterOrders() {
        VBox box = createGlassBox(14, 16);
        Label t = new Label("Mes commandes");
        t.setTextFill(Color.WHITE);
        t.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        Button refresh = createOutlineButton("Rafraîchir");
        refresh.setOnAction(e -> refreshCommandes());
        ordersCardsContainer = new VBox(16);
        ordersCardsContainer.setPadding(new Insets(4, 0, 8, 0));
        ScrollPane sp = new ScrollPane(ordersCardsContainer);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        rebuildOrdersCards();
        box.getChildren().addAll(t, refresh, sp);
        return box;
    }

    private void rebuildOrdersCards() {
        if (ordersCardsContainer == null) {
            return;
        }
        ordersCardsContainer.getChildren().clear();
        if (commandesDetailCache.isEmpty()) {
            Label empty =
                    new Label(
                            "Aucune commande pour cette référence client. Validez une commande depuis le panier, puis"
                                    + " rafraîchissez.");
            empty.setWrapText(true);
            empty.setTextFill(Color.web("#9DB0D4"));
            ordersCardsContainer.getChildren().add(empty);
            return;
        }
        for (SocketApiClient.CommandeFull c : commandesDetailCache) {
            ordersCardsContainer.getChildren().add(buildOrderCard(c));
        }
    }

    private VBox buildOrderCard(SocketApiClient.CommandeFull c) {
        VBox card = createGlassBox(12, 14);
        String when =
                c.dateCommandeMs() > 0
                        ? ORDER_DATE_TIME.format(
                                Instant.ofEpochMilli(c.dateCommandeMs()).atZone(ZoneId.systemDefault()))
                        : "—";
        Label head =
                new Label(
                        "Commande #"
                                + c.id()
                                + " · "
                                + when
                                + " · "
                                + (c.status() != null ? c.status() : "—"));
        head.setWrapText(true);
        head.setTextFill(Color.WHITE);
        head.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        Label total =
                new Label(String.format(Locale.US, "Total commande : %.2f USD", c.total()));
        total.setTextFill(Color.web("#F4C76C"));
        total.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        VBox lines = new VBox(12);
        for (SocketApiClient.OrderLineSnapshot line : c.lignes()) {
            lines.getChildren().add(buildOrderLineRow(line));
        }
        if (lines.getChildren().isEmpty()) {
            Label nx = new Label("Aucun détail d’article pour cette commande.");
            nx.setTextFill(Color.web("#9DB0D4"));
            lines.getChildren().add(nx);
        }
        card.getChildren().addAll(head, total, lines);
        String orderStatus = c.status() != null ? c.status().trim() : "";
        if (accountLoggedIn
                && sessionToken != null
                && !sessionToken.isBlank()
                && "EN_ATTENTE".equalsIgnoreCase(orderStatus)) {
            Button payBtn = createGlowButton("Simuler le paiement", "#2962FF");
            payBtn.setOnAction(ev -> openPaymentDialog(c.id()));
            Button cancelBtn = createOutlineButton("Annuler la commande");
            cancelBtn.setOnAction(ev -> requestCancelCommande(c));
            HBox actions = new HBox(12, payBtn, cancelBtn);
            actions.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(actions);
        }
        return card;
    }

    private HBox buildOrderLineRow(SocketApiClient.OrderLineSnapshot line) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        ImageView iv = new ImageView();
        iv.setFitWidth(80);
        iv.setFitHeight(80);
        iv.setPreserveRatio(true);
        Rectangle clip = new Rectangle(80, 80);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        iv.setClip(clip);

        Product p = findProduct(String.valueOf(line.produitId()));
        String url = p != null && p.getImageUrl() != null ? p.getImageUrl() : null;
        if (url != null && !url.isBlank()) {
            ProductImageLoader.loadAsync(
                    url,
                    80,
                    80,
                    img -> iv.setImage(img),
                    () -> iv.setImage(null));
        }

        Label name = new Label(line.nom());
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        name.setWrapText(true);
        name.setMaxWidth(420);

        double sub = line.quantite() * line.prixUnitaire();
        Label meta =
                new Label(
                        "Quantité : "
                                + line.quantite()
                                + " · "
                                + String.format(Locale.US, "%.2f USD", line.prixUnitaire())
                                + " / unité · Sous-total "
                                + String.format(Locale.US, "%.2f USD", sub));
        meta.setWrapText(true);
        meta.setTextFill(Color.web("#9DB0D4"));
        meta.setFont(Font.font(12));

        VBox txt = new VBox(6, name, meta);
        HBox.setHgrow(txt, Priority.ALWAYS);
        row.getChildren().addAll(iv, txt);
        return row;
    }

    private HBox buildTopBar() {
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox searchBar = createGlassHBox(12);
        searchBar.setPadding(new Insets(10, 14, 10, 14));
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.setTextFill(Color.WHITE);
        searchField = new TextField();
        searchField.setPromptText("Rechercher dans les produits affichés…");
        searchField.setStyle(searchFieldInsideStyle());
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchSuggestions = new ContextMenu();
        searchField.textProperty().addListener((o, a, n) -> refreshProductGrid());
        searchField.textProperty().addListener((o, a, n) -> refreshSearchSuggestions(n));
        searchField.setOnAction(
                e -> {
                    if (searchSuggestions != null) {
                        searchSuggestions.hide();
                    }
                });

        searchBar.getChildren().addAll(searchIcon, searchField);

        Label catLbl = new Label("Catégorie");
        catLbl.setTextFill(Color.web("#A8B4D1"));
        categoryCombo = new ComboBox<>();
        categoryCombo.setPromptText("Tous");
        categoryCombo.setPrefWidth(200);
        categoryCombo.setStyle(comboBoxDarkStyle());
        categoryCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                loadCatalogForCategory(n);
            }
        });

        Button reload = createOutlineButton("Recharger catalogue");
        reload.setOnAction(e -> {
            if (categoryCombo.getValue() != null) {
                loadCatalogForCategory(categoryCombo.getValue());
            } else {
                loadCatalogForCategory("Tous");
            }
        });

        top.getChildren().addAll(searchBar, catLbl, categoryCombo, reload);
        return top;
    }

    private VBox buildHero() {
        VBox hero = createGlassBox(20, 18);
        hero.setStyle(hero.getStyle()
                + "-fx-background-color: linear-gradient(to bottom right, rgba(3,10,30,0.92), rgba(17,31,75,0.92));");

        Label title = new Label("Boutique ChriOnline");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 28));

        Label subtitle =
                new Label(
                        "Parcourez le catalogue, ajoutez au panier, puis validez votre commande et le paiement depuis les onglets prévus."
                );
        subtitle.setWrapText(true);
        subtitle.setTextFill(Color.web("#B8C5E0"));
        subtitle.setFont(Font.font(14));

        HBox badges =
                new HBox(
                        10,
                        buildHeroBadge("Boutique", "#2962FF"),
                        buildHeroBadge("Favoris", "#F43F5E"),
                        buildHeroBadge("Tableau", "#8B5CF6")
                );
        hero.getChildren().addAll(title, subtitle, badges);
        return hero;
    }

    private Label buildHeroBadge(String text, String color) {
        Label badge = new Label(text);
        badge.setTextFill(Color.WHITE);
        badge.setStyle(
                "-fx-background-color: " + color + ";"
                        + "-fx-background-radius: 999;"
                        + "-fx-padding: 6 12 6 12;"
                        + "-fx-font-weight: bold;"
        );
        return badge;
    }

    private VBox buildProductsSection() {
        VBox section = new VBox(12);

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Catalogue");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        catalogSubtitleLabel = new Label("Chargement du catalogue…");
        catalogSubtitleLabel.setTextFill(Color.web("#8FA2C9"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(new VBox(2, title, catalogSubtitleLabel), spacer);

        productsGrid = new TilePane();
        productsGrid.setHgap(12);
        productsGrid.setVgap(12);
        productsGrid.setPrefColumns(4);
        productsGrid.setTileAlignment(Pos.TOP_LEFT);

        section.getChildren().addAll(titleRow, productsGrid);
        return section;
    }

    private ScrollPane buildRightPanel() {
        VBox inner = new VBox(12);
        inner.setPrefWidth(328);
        inner.setMinWidth(328);
        inner.setMaxWidth(328);

        VBox userCard = createGlassBox(12, 14);
        Label u = new Label("Référence client (commandes)");
        u.setTextFill(Color.WHITE);
        u.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        userIdField = new TextField("1");
        userIdField.setStyle(inputStyle());
        userIdField.textProperty().addListener((o, a, n) -> parseUserId());
        userCard.getChildren().addAll(u, userIdField);

        VBox dashboardCard = createGlassBox(10, 14);
        Label d = new Label("Tableau");
        d.setTextFill(Color.WHITE);
        d.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        dashboardProductsLabel = metricLabel("Produits en mémoire : 0");
        dashboardCategoriesLabel = metricLabel("Catégories visibles : 0");
        dashboardFavoritesLabel = metricLabel("Favoris : 0");
        dashboardCartLabel = metricLabel("Articles panier : 0");
        dashboardRevenueLabel = metricLabel("Total panier : 0.00 USD");
        dashboardOrdersLabel = metricLabel("Commandes visibles : 0");
        dashboardCard.getChildren()
                .addAll(
                        d,
                        dashboardProductsLabel,
                        dashboardCategoriesLabel,
                        dashboardFavoritesLabel,
                        dashboardCartLabel,
                        dashboardRevenueLabel,
                        dashboardOrdersLabel
                );

        VBox cartCard = createGlassBox(12, 14);
        Label c = new Label("Panier");
        c.setTextFill(Color.WHITE);
        c.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        cartSummaryLabel = new Label("Vide");
        cartSummaryLabel.setTextFill(Color.web("#FDE68A"));
        cartSummaryLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        cartSummaryLabel.setWrapText(true);
        Button addOrder = createGlowButton("Créer commande", "#18C964");
        addOrder.setMaxWidth(Double.MAX_VALUE);
        addOrder.setOnAction(e -> createCommandeFromCart());
        cartCard.getChildren().addAll(c, cartSummaryLabel, addOrder);

        VBox favCard = createGlassBox(12, 14);
        Label favTitle = new Label("Favoris");
        favTitle.setTextFill(Color.WHITE);
        favTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        favoritesList = new ListView<>();
        favoritesList.setItems(FXCollections.observableArrayList());
        applyFavoritesListView(favoritesList);
        Button clearFav = createOutlineButton("Vider favoris");
        clearFav.setMaxWidth(Double.MAX_VALUE);
        clearFav.setOnAction(
                e -> {
                    wishlist.clear();
                    updateFavoritesUI();
                    refreshProductGrid();
                    updateDashboardStats();
                });
        favCard.getChildren().addAll(favTitle, favoritesList, clearFav);

        VBox payCard = createGlassBox(12, 14);
        Label p = new Label("Paiement");
        p.setTextFill(Color.WHITE);
        p.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        Label hint = new Label("Simulation de paiement (module dédié).");
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#E2E8F0"));
        hint.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        Label savedTitle = new Label("Moyens enregistrés");
        savedTitle.setTextFill(Color.web("#FDE68A"));
        savedTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        savedPaymentMethodsBox = new VBox(8);
        savedPaymentMethodsBox.setFillWidth(true);
        Button refreshSavedBtn = createOutlineButton("Actualiser la liste");
        refreshSavedBtn.setMaxWidth(Double.MAX_VALUE);
        refreshSavedBtn.setOnAction(e -> refreshSavedPaymentMethods());

        TextArea methods = new TextArea(buildPaymentMethodsText());
        methods.setEditable(false);
        methods.setWrapText(true);
        methods.setPrefHeight(72);
        methods.setStyle(textAreaDarkStyle());
        lastPaymentRiskLabel = metricLabel("Indicateur de risque : —");
        Button payBtn = createGlowButton("Simuler paiement…", "#2962FF");
        payBtn.setMaxWidth(Double.MAX_VALUE);
        payBtn.setOnAction(e -> openPaymentDialog());
        Button invoiceBtn = createOutlineButton("Dernière facture (texte)");
        invoiceBtn.setMaxWidth(Double.MAX_VALUE);
        invoiceBtn.setOnAction(e -> openInvoiceDialog());
        payCard.getChildren()
                .addAll(
                        p,
                        hint,
                        savedTitle,
                        savedPaymentMethodsBox,
                        refreshSavedBtn,
                        methods,
                        lastPaymentRiskLabel,
                        payBtn,
                        invoiceBtn);

        inner.getChildren().addAll(userCard, dashboardCard, cartCard, favCard, payCard);

        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setPrefWidth(332);
        scroll.setMinWidth(332);
        scroll.setMaxWidth(332);
        BorderPane.setAlignment(scroll, Pos.TOP_RIGHT);
        return scroll;
    }

    private Label metricLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#E8EDF7"));
        l.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 13));
        l.setWrapText(true);
        l.setStyle("-fx-line-spacing: 3px;");
        return l;
    }

    private void parseUserId() {
        try {
            userId = Integer.parseInt(userIdField.getText().trim());
        } catch (NumberFormatException ignored) {
            userId = 1;
        }
    }

    /**
     * Aligne {@link #host}, {@link #port} et {@link #api} sur les champs « Hôte » / « Port » pour que
     * Connexion / Inscription utilisent la même cible que « Connecter ».
     */
    private void rebuildApiFromFields() {
        if (hostField != null) {
            host = hostField.getText().trim();
        }
        if (portField != null) {
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException e) {
                port = DEFAULT_PORT;
            }
        }
        if (api != null) {
            api.closeQuietly();
        }
        api = new SocketApiClient(host, port);
        api.setApplicationCryptoEnabled(true);
    }

    /**
     * Ligne sous l’état réseau : « Connecter » teste le socket ; « Se connecter » (section COMPTE) envoie LOGIN.
     */
    private void refreshSessionBanner() {
        if (sessionLabel == null) {
            return;
        }
        if (accountLoggedIn) {
            String u = sessionUsername.isEmpty() ? "Bienvenue" : sessionUsername;
            String suffix =
                    "ADMIN".equalsIgnoreCase(sessionRole != null ? sessionRole.trim() : "")
                            ? " · Administrateur"
                            : "";
            sessionLabel.setText("Compte connecté · " + u + suffix);
            sessionLabel.setTextFill(Color.web("#8DF0BC"));
            return;
        }
        if (serverReachable) {
            sessionLabel.setText(
                    "Compte : non connecté — remplissez e-mail et mot de passe sous COMPTE, puis « Se connecter ».");
            sessionLabel.setTextFill(Color.web("#FDE68A"));
        } else {
            sessionLabel.setText(
                    "Compte : non connecté — cliquez d’abord « Connecter », puis utilisez « Se connecter ».");
            sessionLabel.setTextFill(Color.web("#A8B4D1"));
        }
    }

    /**
     * Picks host/port from: JVM props, {@code CHRIONLINE_SERVER_HOST}/PORT env, merged client properties
     * (classpath + {@code ~/.chrionline/chrionline-client.properties}), last session, then LAN discovery.
     * Loopback is allowed when {@link #allowLoopbackHost} is true (default).
     */
    private void discoverAndConnect() {
        runAsync(
                () -> {
                    String sysHost = System.getProperty("chrionline.server.host", "").trim();
                    String sysPortStr = System.getProperty("chrionline.server.port", "").trim();
                    int sysPort = DEFAULT_PORT;
                    if (!sysPortStr.isEmpty()) {
                        try {
                            sysPort = Integer.parseInt(sysPortStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    String envHost = System.getenv("CHRIONLINE_SERVER_HOST");
                    if (envHost != null) {
                        envHost = envHost.trim();
                    }
                    String envPortStr = System.getenv("CHRIONLINE_SERVER_PORT");
                    int envPort = DEFAULT_PORT;
                    if (envPortStr != null && !envPortStr.isBlank()) {
                        try {
                            envPort = Integer.parseInt(envPortStr.trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    String fileHost = clientConfig.getProperty("server.host", "").trim();
                    String filePortStr = clientConfig.getProperty("server.port", "").trim();
                    int filePort = DEFAULT_PORT;
                    if (!filePortStr.isEmpty()) {
                        try {
                            filePort = Integer.parseInt(filePortStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    boolean discoveryEnabled =
                            Boolean.parseBoolean(clientConfig.getProperty("discovery.enabled", "true"));
                    int discoveryTimeout = 5000;
                    try {
                        discoveryTimeout =
                                Integer.parseInt(clientConfig.getProperty("discovery.timeout.ms", "5000"));
                    } catch (NumberFormatException ignored) {
                    }
                    int discoveryRounds = 4;
                    try {
                        discoveryRounds =
                                Integer.parseInt(clientConfig.getProperty("discovery.retry.rounds", "4"));
                    } catch (NumberFormatException ignored) {
                    }
                    int discoveryPause = 600;
                    try {
                        discoveryPause =
                                Integer.parseInt(clientConfig.getProperty("discovery.retry.pause.ms", "600"));
                    } catch (NumberFormatException ignored) {
                    }
                    String lastHost = ClientPrefs.getString(PREF_CLIENT, "lastServerHost", "").trim();
                    int lastPort = ClientPrefs.getInt(PREF_CLIENT, "lastServerPort", DEFAULT_PORT);
                    if (isForbiddenLoopbackHost(lastHost)) {
                        lastHost = "";
                    }

                    String chosenHost = null;
                    int chosenPort = DEFAULT_PORT;
                    if (!sysHost.isEmpty()
                            && !isForbiddenLoopbackHost(sysHost)
                            && NetworkInfo.isLikelyReachableFromOtherMachines(sysHost)) {
                        chosenHost = sysHost;
                        chosenPort = sysPort;
                    } else if (envHost != null
                            && !envHost.isEmpty()
                            && !isForbiddenLoopbackHost(envHost)
                            && NetworkInfo.isLikelyReachableFromOtherMachines(envHost)) {
                        chosenHost = envHost;
                        chosenPort = envPort;
                    } else if (!fileHost.isEmpty()
                            && !isForbiddenLoopbackHost(fileHost)
                            && NetworkInfo.isLikelyReachableFromOtherMachines(fileHost)) {
                        chosenHost = fileHost;
                        chosenPort = filePort;
                    } else if (!lastHost.isEmpty()
                            && !isForbiddenLoopbackHost(lastHost)
                            && NetworkInfo.isLikelyReachableFromOtherMachines(lastHost)) {
                        chosenHost = lastHost;
                        chosenPort = lastPort;
                    } else if (discoveryEnabled) {
                        Platform.runLater(
                                () -> {
                                    connectionLabel.setText(
                                            "Recherche du serveur ChriOnline (multicast / diffusion)…");
                                    connectionLabel.setTextFill(Color.web("#7DD3FC"));
                                });
                        Optional<InetSocketAddress> found =
                                LanDiscoveryClient.discoverWithRetries(
                                        discoveryTimeout, discoveryRounds, discoveryPause);
                        if (found.isPresent()) {
                            String h = found.get().getHostString();
                            if (!isForbiddenLoopbackHost(h)
                                    && NetworkInfo.isLikelyReachableFromOtherMachines(h)) {
                                chosenHost = h;
                                chosenPort = found.get().getPort();
                            }
                        }
                    }
                    final String fh = chosenHost;
                    final int fp = chosenPort;
                    final int filePortFinal = filePort;
                    Platform.runLater(
                            () -> {
                                if (fh != null && !fh.isBlank()) {
                                    hostField.setText(fh);
                                    portField.setText(String.valueOf(fp));
                                    connectToServer();
                                } else {
                                    if (allowLoopbackHost) {
                                        hostField.setText("127.0.0.1");
                                        portField.setText(String.valueOf(filePortFinal));
                                        connectionLabel.setText(
                                                "Connexion locale (127.0.0.1). Pour un autre PC : IPv4 du serveur.");
                                    } else {
                                        hostField.setText("");
                                        portField.setText(String.valueOf(filePortFinal));
                                        connectionLabel.setText(
                                                "Saisissez l’IP ou le domaine du serveur (localhost désactivé dans la configuration).");
                                    }
                                    connectionLabel.setTextFill(Color.web("#FFB86B"));
                                }
                            });
                });
    }

    /** {@code true} when loopback host names must be rejected ({@link #allowLoopbackHost} {@code false}). */
    private boolean isForbiddenLoopbackHost(String h) {
        if (allowLoopbackHost) {
            return false;
        }
        if (h == null || h.isBlank()) {
            return false;
        }
        String s = h.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(s)) {
            return true;
        }
        if ("127.0.0.1".equals(s)) {
            return true;
        }
        if ("::1".equals(s) || "[::1]".equals(s)) {
            return true;
        }
        return "0:0:0:0:0:0:0:1".equals(s);
    }

    private String friendlySocketError(Throwable ex) {
        Throwable c = ex;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = c.getMessage() != null ? c.getMessage().toLowerCase(Locale.ROOT) : "";
        if (c instanceof java.net.ConnectException || msg.contains("connection refused")) {
            return "Connexion refusée — le serveur ChriOnline est-il démarré sur le PC hôte ? Vérifiez aussi le pare-feu (TCP "
                    + port
                    + ").";
        }
        if (c instanceof java.net.SocketTimeoutException || msg.contains("timed out")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Délai dépassé — pare-feu ou adresse injoignable (TCP ").append(port).append("). ");
            if (NetworkInfo.isNotRoutableFromOtherNetworks(host)) {
                sb.append(
                        "Les IP 192.168.x / 10.x ne sont pas des adresses Internet : depuis un autre réseau Wi‑Fi "
                                + "ou la 4G, utilisez l’IP publique du routeur du serveur + redirection de port, "
                                + "ou un VPN (ex. Tailscale). La console serveur affiche une IP publique indicative.");
            } else {
                sb.append("Vérifiez la redirection de port sur le routeur du serveur et le pare-feu Windows.");
            }
            return sb.toString();
        }
        if (msg.contains("network is unreachable") || msg.contains("no route to host")) {
            return "Réseau injoignable — vérifiez l’adresse (LAN ou IP publique / DNS) et la connectivité.";
        }
        String detail = c.getMessage();
        return "Connexion impossible"
                + (detail != null && !detail.isBlank() ? " — " + detail : "")
                + ".";
    }

    private void connectToServer() {
        rebuildApiFromFields();
        if (host.isBlank()) {
            serverReachable = false;
            refreshSessionBanner();
            connectionLabel.setText(
                    allowLoopbackHost
                            ? "Indiquez l’hôte (ex. 127.0.0.1 sur cette machine) ou utilisez la valeur proposée."
                            : "Indiquez l’hôte du serveur (IPv4, hostname ou domaine).");
            connectionLabel.setTextFill(Color.web("#FFB86B"));
            return;
        }
        if (isForbiddenLoopbackHost(host)) {
            serverReachable = false;
            refreshSessionBanner();
            connectionLabel.setText(
                    "localhost / 127.0.0.1 désactivés — mettez client.allow.loopback=true ou utilisez l’IPv4 LAN.");
            connectionLabel.setTextFill(Color.web("#FF8A8A"));
            return;
        }
        if (!NetworkInfo.isLikelyReachableFromOtherMachines(host)) {
            serverReachable = false;
            refreshSessionBanner();
            connectionLabel.setText(
                    "Adresse host-only (ex. VirtualBox 192.168.56.x) — les autres PC ne peuvent pas s’y connecter. "
                            + "Utilisez l’IPv4 Wi‑Fi / Ethernet indiquée dans la console du serveur ChriOnline.");
            connectionLabel.setTextFill(Color.web("#FF8A8A"));
            return;
        }
        showLoading(true);

        runAsync(() -> {
            try {
                long t0 = System.currentTimeMillis();
                Message req = Message.request("PING", "1", "");
                Message res = api.send(req);
                long ms = System.currentTimeMillis() - t0;
                if ("SUCCESS".equals(res.getStatus()) && "PONG".equals(res.getPayload())) {
                    ClientPrefs.putString(PREF_CLIENT, "lastServerHost", host);
                    ClientPrefs.putInt(PREF_CLIENT, "lastServerPort", port);
                    String tokCheck = sessionToken;
                    boolean hadPersistedToken =
                            accountLoggedIn && tokCheck != null && !tokCheck.isBlank();
                    if (hadPersistedToken) {
                        try {
                            Message probe =
                                    api.send(
                                            Message.request(
                                                    "GET_COMMANDES",
                                                    "8",
                                                    "{\"sessionToken\":\""
                                                            + jsonEsc(tokCheck)
                                                            + "\"}"));
                            if (!"SUCCESS".equals(probe.getStatus())
                                    && "SESSION_INVALID".equals(probe.getErrorCode())) {
                                Platform.runLater(
                                        () -> {
                                            clearLocalSessionBecauseInvalidOnServer();
                                            showAppToast(
                                                    "Session plus valide sur ce serveur (souvent après un redémarrage)."
                                                            + " Reconnectez-vous depuis l’onglet Compte.",
                                                    Alert.AlertType.WARNING);
                                        });
                            }
                        } catch (Exception ignored) {
                            // laisser l’utilisateur rafraîchir Commandes plus tard
                        }
                    }
                    Platform.runLater(() -> {
                        serverReachable = true;
                        String wire = (api != null && api.isApplicationCryptoSessionActive()) ? " · Session RSA→AES" : "";
                        connectionLabel.setText("En ligne · " + host + ":" + port + " · " + ms + " ms" + wire);
                        connectionLabel.setTextFill(Color.web("#8DF0BC"));
                        showLoading(false);
                        refreshSessionBanner();
                        refreshSavedPaymentMethods();
                    });
                    loadCategoryList();
                    loadCatalogForCategory("Tous");
                } else {
                    String err = res != null ? res.getErrorCode() : "";
                    boolean dbDown = "DB_UNAVAILABLE".equals(err);
                    Platform.runLater(() -> {
                        serverReachable = false;
                        refreshSessionBanner();
                        connectionLabel.setText(
                                dbDown
                                        ? "Serveur joignable, service de données indisponible sur l'hôte"
                                        : "Réponse inattendue"
                        );
                        connectionLabel.setTextFill(Color.web("#FFB4B4"));
                        showLoading(false);
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    serverReachable = false;
                    refreshSessionBanner();
                    connectionLabel.setText(friendlySocketError(ex));
                    connectionLabel.setTextFill(Color.web("#FF8A8A"));
                    showLoading(false);
                });
            }
        });
    }

    private void loadCategoryList() {
        runAsync(() -> {
            try {
                Message res = api.send(Message.request("PRODUCT_CATEGORIES", "99", ""));
                if (!"SUCCESS".equals(res.getStatus())) {
                    return;
                }
                List<String> cats = SocketApiClient.parseStringArray(res.getPayload());
                Platform.runLater(() -> {
                    categoryCombo.getItems().setAll(cats);
                    if (!cats.isEmpty() && categoryCombo.getValue() == null) {
                        categoryCombo.setValue(cats.contains("Tous") ? "Tous" : cats.get(0));
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private String buildPagedListPayload(String category, int offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"limit\":").append(CATALOG_PAGE_SIZE).append(",\"offset\":").append(offset);
        if (category != null && !category.isBlank() && !"Tous".equalsIgnoreCase(category.trim())) {
            sb.append(",\"category\":\"").append(jsonEsc(category.trim())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /** First page or full reload (category change, Recharger). */
    private void loadCatalogForCategory(String category) {
        String c = category == null || category.isBlank() ? "Tous" : category.trim();
        currentCatalogCategory = c;
        catalogLoadOffset = 0;
        catalog.clear();
        productById.clear();
        catalogHasMore = true;
        showLoading(true);
        runAsync(
                () -> {
                    try {
                        fetchCatalogPage(true, true);
                    } catch (Exception ex) {
                        Platform.runLater(
                                () -> {
                                    catalogLoading = false;
                                    showLoading(false);
                                    alertThrowable(ex);
                                });
                    }
                });
    }

    /** Infinite scroll — next page, no full-screen overlay. */
    private void loadMoreCatalog() {
        if (!catalogHasMore || catalogLoading) {
            return;
        }
        catalogLoading = true;
        runAsync(
                () -> {
                    try {
                        fetchCatalogPage(false, false);
                    } catch (Exception ex) {
                        Platform.runLater(
                                () -> {
                                    catalogLoading = false;
                                    alertThrowable(ex);
                                });
                    }
                });
    }

    private void fetchCatalogPage(boolean resetScroll, boolean dismissOverlay) throws Exception {
        if (!catalogLoading) {
            catalogLoading = true;
        }
        String payload = buildPagedListPayload(currentCatalogCategory, catalogLoadOffset);
        Message res = api.send(Message.request("PRODUCT_LIST", "4", payload));
        List<Product> list = api.fetchProductList(res);
        Platform.runLater(
                () -> {
                    for (Product p : list) {
                        catalog.add(p);
                        productById.put(p.getId(), p);
                    }
                    catalogLoadOffset += list.size();
                    catalogHasMore = list.size() >= CATALOG_PAGE_SIZE;
                    if (catalogSubtitleLabel != null) {
                        catalogSubtitleLabel.setText(
                                catalog.size()
                                        + " produit(s) affiché(s)"
                                        + (catalogHasMore
                                        ? " · faites défiler pour charger plus"
                                        : " · fin du catalogue")
                                        + " · prix en USD");
                    }
                    if (resetScroll && homeScrollPane != null) {
                        homeScrollPane.setVvalue(0);
                    }
                    refreshProductGrid();
                    refreshTopSellers();
                    updateDashboardStats();
                    if (dismissOverlay) {
                        showLoading(false);
                    }
                    catalogLoading = false;
                });
    }

    private static String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void refreshProductGrid() {
        if (productsGrid == null) {
            return;
        }
        productsGrid.getChildren().clear();
        String q = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        List<Product> filtered =
                catalog.stream()
                        .filter(
                                p -> q.isEmpty()
                                        || p.getName().toLowerCase(Locale.ROOT).contains(q)
                                        || (p.getDescription() != null
                                        && p.getDescription().toLowerCase(Locale.ROOT).contains(q))
                                        || p.getId().toLowerCase(Locale.ROOT).contains(q)
                                        || (p.getCategory() != null
                                        && p.getCategory().toLowerCase(Locale.ROOT).contains(q))
                                        || (p.getBrand() != null
                                        && p.getBrand().toLowerCase(Locale.ROOT).contains(q)))
                        .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label(catalog.isEmpty()
                    ? "Connectez-vous et chargez le catalogue (bouton Recharger)."
                    : "Aucun produit ne correspond à la recherche.");
            empty.setTextFill(Color.web("#9DB0D4"));
            empty.setWrapText(true);
            productsGrid.getChildren().add(empty);
            return;
        }

        final int maxCards = 200;
        List<Product> slice = filtered.size() > maxCards ? filtered.subList(0, maxCards) : filtered;
        for (Product p : slice) {
            productsGrid.getChildren().add(createProductCard(p));
        }
        if (filtered.size() > maxCards) {
            Label cap =
                    new Label(
                            "Affichage limité aux "
                                    + maxCards
                                    + " premiers résultats — affinez la recherche.");
            cap.setTextFill(Color.web("#9DB0D4"));
            cap.setWrapText(true);
            productsGrid.getChildren().add(cap);
        }
    }

    private VBox createProductCard(Product p) {
        return createProductCard(p, false);
    }

    private VBox createProductCard(Product p, boolean topSeller) {
        boolean wide = topSeller;
        VBox card = createGlassBox(10, 12);
        card.setPrefWidth(wide ? 280 : 230);
        card.setPrefHeight(wide ? 360 : 350);

        StackPane visual = new StackPane();
        int imgW = wide ? 250 : 200;
        int imgH = 128;
        visual.setPrefSize(imgW, imgH);

        Rectangle backdrop = new Rectangle(imgW, imgH);
        backdrop.setArcWidth(20);
        backdrop.setArcHeight(20);
        backdrop.setFill(
                new LinearGradient(
                        0,
                        0,
                        1,
                        1,
                        true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#1e293b", 0.9)),
                        new Stop(1, Color.web("#0f172a", 0.95))));
        backdrop.setStroke(Color.web("#FFFFFF", 0.08));

        ImageView imageView = new ImageView();
        imageView.setFitWidth(imgW);
        imageView.setFitHeight(imgH);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        Rectangle clip = new Rectangle(imgW, imgH);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        imageView.setClip(clip);

        Label ph = new Label("#" + p.getId());
        ph.setTextFill(Color.web("#DCE6FA"));
        ph.setTranslateY(52);
        ph.setMouseTransparent(true);

        String url = p.getImageUrl();
        if (url != null && !url.isBlank()) {
            ProductImageLoader.loadAsync(
                    url,
                    imgW,
                    imgH,
                    img -> {
                        imageView.setImage(img);
                        ph.setVisible(false);
                    },
                    () -> {
                        ph.setText("#" + p.getId() + " · pas d'image");
                        ph.setVisible(true);
                    });
        }

        Button favBtn = new Button(wishlist.contains(p.getId()) ? "♥" : "♡");
        favBtn.setTextFill(Color.WHITE);
        favBtn.setStyle(
                "-fx-background-color: rgba(244,63,94,0.85);"
                        + "-fx-background-radius: 999;"
                        + "-fx-font-weight: bold;"
        );
        favBtn.setOnAction(
                e -> {
                    toggleWishlist(p.getId());
                    favBtn.setText(wishlist.contains(p.getId()) ? "♥" : "♡");
                });
        StackPane.setAlignment(favBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(favBtn, new Insets(8));

        visual.getChildren().addAll(backdrop, imageView, ph, favBtn);

        Label name = new Label(p.getName());
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        name.setWrapText(true);
        name.setStyle("-fx-cursor: hand;");
        name.setOnMouseClicked(e -> showProductDetail(p));

        Label desc = new Label(shorten(p.getDescription(), wide ? 110 : 90));
        desc.setTextFill(Color.web("#90A2C7"));
        desc.setWrapText(true);
        desc.setFont(Font.font(12));
        desc.setMaxWidth(wide ? 270 : 220);

        String meta = (p.getCategory() != null && !p.getCategory().isEmpty()) ? p.getCategory() : "—";
        if (p.getBrand() != null && !p.getBrand().isEmpty()) {
            meta = p.getBrand() + " · " + meta;
        }
        Label cat = new Label(meta);
        cat.setTextFill(Color.web("#7dd3fc"));
        cat.setFont(Font.font(11));
        cat.setWrapText(true);

        Label price = new Label(String.format(Locale.US, "%.2f USD", p.getPrice()));
        price.setTextFill(Color.WHITE);
        price.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, 17));

        String ratingTxt = p.getRating() > 0 ? String.format(Locale.US, "★ %.2f", p.getRating()) : "★ —";
        Label rating = new Label(ratingTxt);
        rating.setTextFill(Color.web("#F4C76C"));
        rating.setFont(Font.font(12));

        Label stock = new Label("Stock : " + p.getStock());
        stock.setTextFill(Color.web("#F4C76C"));

        int inCart = cart.getOrDefault(p.getId(), 0);
        Label cartInfo = new Label("Dans le panier : " + inCart);
        cartInfo.setTextFill(Color.web("#8AB2FF"));

        Button add = createGlowButton("Ajouter", "#2962FF");
        add.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(add, Priority.ALWAYS);
        Button quick = createOutlineButton("Détails");
        HBox.setHgrow(quick, Priority.ALWAYS);
        add.setOnAction(
                e -> {
                    int q = cart.getOrDefault(p.getId(), 0) + 1;
                    if (q <= p.getStock()) {
                        cart.put(p.getId(), q);
                    } else {
                        alert(Alert.AlertType.WARNING, "Stock insuffisant.");
                    }
                    updateCartSummary();
                    refreshProductGrid();
                    refreshTopSellers();
                    updateDashboardStats();
                });
        quick.setOnAction(e -> showProductDetail(p));
        HBox actions = new HBox(8, add, quick);

        if (topSeller) {
            Label badge = new Label("Top Seller");
            badge.setTextFill(Color.WHITE);
            badge.setStyle(
                    "-fx-background-color: rgba(124,58,237,0.90); -fx-background-radius: 999; -fx-padding: 4 10 4 10;");
            card.getChildren().addAll(visual, badge, name, cat, desc, price, rating, stock, cartInfo, actions);
        } else {
            card.getChildren().addAll(visual, name, cat, desc, price, rating, stock, cartInfo, actions);
        }
        addHoverZoom(card);
        return card;
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private void updateCartSummary() {
        if (cartSummaryLabel == null) {
            return;
        }
        if (cart.isEmpty()) {
            cartSummaryLabel.setText("Vide");
            updateDashboardStats();
            return;
        }
        double total = 0;
        int items = 0;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = findProduct(e.getKey());
            int q = e.getValue();
            items += q;
            if (p != null) {
                total += p.getPrice() * q;
                sb.append(p.getName()).append(" ×").append(q).append("\n");
            }
        }
        cartSummaryLabel.setText(items + " article(s) · " + String.format(Locale.US, "%.2f USD", total) + "\n" + sb);
        updateDashboardStats();
    }

    private Product findProduct(String id) {
        if (id == null) {
            return null;
        }
        Product p = productById.get(id);
        if (p != null) {
            return p;
        }
        for (Product x : catalog) {
            if (x.getId().equals(id)) {
                return x;
            }
        }
        return null;
    }

    private void toggleWishlist(String productId) {
        if (wishlist.contains(productId)) {
            wishlist.remove(productId);
        } else {
            wishlist.add(productId);
        }
        updateFavoritesUI();
        refreshTopSellers();
        refreshProductGrid();
        updateDashboardStats();
    }

    private void updateFavoritesUI() {
        if (favoritesList == null) {
            return;
        }
        favoritesList.setItems(FXCollections.observableArrayList(new ArrayList<>(wishlist)));
        int n = wishlist.size();
        favoritesList.setPrefHeight(Math.min(220, Math.max(52, n * 34 + 14)));
        favoritesList.setMinHeight(52);
    }

    private void refreshSearchSuggestions(String query) {
        if (searchField == null || searchSuggestions == null) {
            return;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            searchSuggestions.hide();
            return;
        }
        List<String> candidates = new ArrayList<>();
        for (Product p : catalog) {
            if (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(q)) {
                candidates.add(p.getName());
            }
        }
        candidates = candidates.stream().distinct().limit(6).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            searchSuggestions.hide();
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        for (String s : candidates) {
            MenuItem mi = new MenuItem(s);
            mi.setOnAction(
                    e -> {
                        searchField.setText(s);
                        searchField.positionCaret(s.length());
                        searchSuggestions.hide();
                    });
            items.add(mi);
        }
        searchSuggestions.getItems().setAll(items);
        if (!searchSuggestions.isShowing()) {
            searchSuggestions.show(searchField, Side.BOTTOM, 0, 4);
        }
    }

    private void refreshTopSellers() {
        if (topSellerGrid == null || topSellerSubtitleLabel == null) {
            return;
        }
        topSellerGrid.getChildren().clear();
        List<Product> top = buildTopSellers();
        if (top.isEmpty()) {
            topSellerSubtitleLabel.setText("Chargez le catalogue pour afficher les best sellers.");
            return;
        }
        topSellerSubtitleLabel.setText("Sélection par note et disponibilité (Top Sellers).");
        for (Product p : top) {
            topSellerGrid.getChildren().add(createProductCard(p, true));
        }
    }

    private List<Product> buildTopSellers() {
        if (catalog.isEmpty()) {
            return List.of();
        }
        List<Product> sorted = new ArrayList<>(catalog);
        sorted.sort(
                Comparator.comparing((Product p) -> isBestSeller(p) ? 0 : 1)
                        .thenComparing(Product::getRating, Comparator.reverseOrder())
                        .thenComparing(Product::getStock, Comparator.reverseOrder()));
        return sorted.stream().limit(3).collect(Collectors.toList());
    }

    private static boolean isBestSeller(Product p) {
        try {
            return p.getRating() >= 4.5 || p.getStock() >= 80;
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeCat(String s) {
        return s == null ? "" : s;
    }

    private void updateDashboardStats() {
        if (dashboardProductsLabel == null) {
            return;
        }
        long categories =
                catalog.stream().map(p -> safeCat(p.getCategory())).filter(s -> !s.isBlank()).distinct().count();
        int cartItems = cart.values().stream().mapToInt(Integer::intValue).sum();
        double cartTotal = currentCartTotal();
        dashboardProductsLabel.setText("Produits en mémoire : " + catalog.size());
        dashboardCategoriesLabel.setText("Catégories visibles : " + categories);
        dashboardFavoritesLabel.setText("Favoris : " + wishlist.size());
        dashboardCartLabel.setText("Articles panier : " + cartItems);
        dashboardRevenueLabel.setText("Total panier : " + String.format(Locale.US, "%.2f USD", cartTotal));
        dashboardOrdersLabel.setText("Commandes visibles : " + commandesDetailCache.size());
    }

    private double currentCartTotal() {
        double total = 0;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            Product p = findProduct(e.getKey());
            if (p != null) {
                total += p.getPrice() * e.getValue();
            }
        }
        return total;
    }

    private void openInvoiceDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(primaryStage);
        dlg.setTitle("Dernière facture");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        TextArea txt = new TextArea(lastInvoiceText.isEmpty() ? "Aucune facture simulée pour cette session." : lastInvoiceText);
        txt.setEditable(false);
        txt.setWrapText(true);
        txt.setPrefRowCount(14);
        txt.setPrefWidth(480);
        txt.setStyle(textAreaDarkStyle());
        dlg.getDialogPane().setContent(txt);
        dlg.showAndWait();
    }

    private String buildProduitsPayload() {
        if (cart.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            int pid = Integer.parseInt(e.getKey().trim());
            parts.add(pid + ":" + e.getValue());
        }
        return String.join(";", parts);
    }

    private void createCommandeFromCart() {
        String payload = buildProduitsPayload();
        if (payload.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Panier vide.");
            return;
        }
        parseUserId();
        String json = "{\"userId\":\"" + userId + "\",\"produits\":\"" + payload + "\"}";
        runAsync(() -> {
            try {
                Message res = api.send(Message.request("CREATE_COMMANDE", "7", json));
                Platform.runLater(() -> {
                    if ("SUCCESS".equals(res.getStatus())) {
                        lastCreatedCommandeId = SocketApiClient.parseCommandeId(res.getPayload());
                        alert(Alert.AlertType.INFORMATION,
                                "Commande enregistrée. Vous pouvez la consulter et la régler dans l'onglet Commandes.");
                        cart.clear();
                        updateCartSummary();
                        refreshProductGrid();
                        refreshTopSellers();
                    } else {
                        alertError(res.getErrorCode());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> alertThrowable(ex));
            }
        });
    }

    /** Annule une commande encore en attente : message {@code ANNULER_COMMANDE} → statut {@code ANNULEE} en base. */
    private void requestCancelCommande(SocketApiClient.CommandeFull c) {
        if (!accountLoggedIn || sessionToken == null || sessionToken.isBlank()) {
            showAppToast("Connectez-vous pour gérer vos commandes.", Alert.AlertType.INFORMATION);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(primaryStage);
        confirm.setTitle("Annuler la commande");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Confirmer l’annulation de la commande n°"
                        + c.id()
                        + " ? Le statut passera à ANNULEE et la commande ne pourra plus être payée.");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }
        runAsync(
                () -> {
                    try {
                        String payload =
                                "{\"sessionToken\":\""
                                        + jsonEsc(sessionToken)
                                        + "\",\"commandeId\":\""
                                        + c.id()
                                        + "\"}";
                        Message res = api.send(Message.request("ANNULER_COMMANDE", "ac", payload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        showAppToast("Commande annulée.", Alert.AlertType.INFORMATION);
                                        refreshCommandes();
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void refreshCommandes() {
        parseUserId();
        if (!accountLoggedIn || userId <= 0 || sessionToken == null || sessionToken.isBlank()) {
            Platform.runLater(
                    () -> {
                        commandesDetailCache.clear();
                        rebuildOrdersCards();
                        updateDashboardStats();
                    });
            return;
        }
        runAsync(() -> {
            try {
                String payload = "{\"sessionToken\":\"" + jsonEsc(sessionToken) + "\"}";
                Message res = api.send(Message.request("GET_COMMANDES", "8", payload));
                Platform.runLater(() -> {
                    if ("SUCCESS".equals(res.getStatus())) {
                        commandesDetailCache.clear();
                        commandesDetailCache.addAll(SocketApiClient.parseCommandesFull(res.getPayload()));
                        rebuildOrdersCards();
                        updateDashboardStats();
                    } else {
                        commandesDetailCache.clear();
                        rebuildOrdersCards();
                        updateDashboardStats();
                        if ("SESSION_INVALID".equals(res.getErrorCode())) {
                            clearLocalSessionBecauseInvalidOnServer();
                            showAppToast(
                                    "Session expirée ou invalide. Reconnectez-vous depuis l’onglet Compte.",
                                    Alert.AlertType.WARNING);
                        } else {
                            alertError(res.getErrorCode());
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> alertThrowable(ex));
            }
        });
    }

    private void refreshSavedPaymentMethods() {
        if (savedPaymentMethodsBox == null) {
            return;
        }
        parseUserId();
        if (host == null || host.isBlank()) {
            Platform.runLater(
                    () -> {
                        savedPaymentMethodsBox.getChildren().clear();
                        Label l = new Label("Connectez-vous au serveur pour charger les moyens enregistrés.");
                        l.setTextFill(Color.web("#7283A7"));
                        l.setWrapText(true);
                        savedPaymentMethodsBox.getChildren().add(l);
                    });
            return;
        }
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(
                                        Message.request(
                                                "LIST_SAVED_PAYMENT_METHODS",
                                                "spm",
                                                String.valueOf(userId)));
                        Platform.runLater(
                                () -> {
                                    savedPaymentMethodsBox.getChildren().clear();
                                    if (!"SUCCESS".equals(res.getStatus())) {
                                        Label err = new Label("Impossible de charger la liste.");
                                        err.setTextFill(Color.web("#F87171"));
                                        savedPaymentMethodsBox.getChildren().add(err);
                                        return;
                                    }
                                    List<SocketApiClient.SavedPaymentEntry> entries =
                                            SocketApiClient.parseSavedPaymentEntries(res.getPayload());
                                    if (entries.isEmpty()) {
                                        Label empty =
                                                new Label(
                                                        "Aucun moyen enregistré. Cochez « Mémoriser » après un"
                                                                + " paiement réussi.");
                                        empty.setTextFill(Color.web("#7283A7"));
                                        empty.setWrapText(true);
                                        savedPaymentMethodsBox.getChildren().add(empty);
                                        return;
                                    }
                                    for (SocketApiClient.SavedPaymentEntry e : entries) {
                                        savedPaymentMethodsBox.getChildren().add(buildSavedMethodRow(e));
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(
                                () -> {
                                    savedPaymentMethodsBox.getChildren().clear();
                                    Label l = new Label("Réseau indisponible.");
                                    l.setTextFill(Color.web("#7283A7"));
                                    savedPaymentMethodsBox.getChildren().add(l);
                                });
                    }
                });
    }

    private HBox buildSavedMethodRow(SocketApiClient.SavedPaymentEntry e) {
        VBox text = new VBox(2);
        Label line1 = new Label(e.label());
        line1.setTextFill(Color.web("#E8EDF7"));
        line1.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 12));
        line1.setWrapText(true);
        Label line2 = new Label(e.type() + " · " + e.createdAt());
        line2.setTextFill(Color.web("#9DB0D4"));
        line2.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        line2.setWrapText(true);
        text.getChildren().addAll(line1, line2);
        Button del = createOutlineButton("Retirer");
        del.setOnAction(ev -> deleteSavedPaymentMethodRemote(e.id()));
        HBox row = new HBox(10, text, del);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        row.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 12; -fx-padding: 8 10 8 10;"
                        + "-fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 12;");
        return row;
    }

    private void deleteSavedPaymentMethodRemote(int methodId) {
        parseUserId();
        String json = "{\"userId\":\"" + userId + "\",\"idMethode\":\"" + methodId + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("DELETE_SAVED_PAYMENT_METHOD", "dsp", json));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        refreshSavedPaymentMethods();
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private static String paymentLastFourDigits(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() >= 4) {
            return d.substring(d.length() - 4);
        }
        return d;
    }

    private void openPaymentDialog() {
        openPaymentDialog(null);
    }

    /**
     * @param presetCommandeId identifiant de commande pré-rempli (ex. depuis l’onglet Commandes) ; {@code null}
     *     utilise {@link #lastCreatedCommandeId} ou un champ vide.
     */
    private void openPaymentDialog(Integer presetCommandeId) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initOwner(primaryStage);
        dlg.setTitle("Paiement sécurisé (simulation)");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setStyle("-fx-background-color: #0b0e14;");
        dlg.getDialogPane().setPrefWidth(560);

        String cmdDefault =
                presetCommandeId != null
                        ? String.valueOf(presetCommandeId)
                        : (lastCreatedCommandeId != null ? String.valueOf(lastCreatedCommandeId) : "");
        TextField cmdField = new TextField(cmdDefault);
        cmdField.setPromptText("ID commande");
        cmdField.setStyle(inputStyle());

        ComboBox<String> typeBox = new ComboBox<>();
        for (TypePaiement t : TypePaiement.values()) {
            typeBox.getItems().add(t.name());
        }
        typeBox.getSelectionModel().selectFirst();
        typeBox.setStyle(comboBoxDarkStyle());
        typeBox.setMaxWidth(Double.MAX_VALUE);

        TextField couponField = new TextField();
        couponField.setPromptText("Coupon (optionnel, ex. PROMO10)");
        couponField.setStyle(inputStyle());

        Label typeHint = new Label();
        typeHint.setWrapText(true);
        typeHint.setTextFill(Color.web("#9DB0D4"));
        typeHint.setFont(Font.font(11));
        typeHint.setMaxWidth(480);

        Region cardFace = new Region();
        cardFace.setPrefSize(320, 112);
        cardFace.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        cardFace.setStyle(
                "-fx-background-radius: 18; -fx-border-radius: 18;"
                        + "-fx-background-color: linear-gradient(to bottom right, #1e3a5f, #0c1222);"
                        + "-fx-border-color: rgba(255,255,255,0.18); -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45),"
                        + " 14, 0.2, 0, 4);");
        Label cardBrandLbl = new Label("ChriOnline Pay");
        cardBrandLbl.setTextFill(Color.web("#E2E8F0"));
        cardBrandLbl.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        Label cardSub = new Label("Carte — simulation · ne pas utiliser de vraies données sensibles");
        cardSub.setTextFill(Color.web("#93c5fd"));
        cardSub.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        cardSub.setWrapText(true);
        StackPane cardArt = new StackPane(cardFace);
        VBox cardArtLabels = new VBox(4, cardBrandLbl, cardSub);
        cardArtLabels.setPadding(new Insets(14, 18, 14, 18));
        StackPane cardHero = new StackPane(cardArt, cardArtLabels);
        StackPane.setAlignment(cardArtLabels, Pos.TOP_LEFT);

        TextField holderField = new TextField();
        holderField.setPromptText("Titulaire (comme sur la carte)");
        holderField.setStyle(inputStyle());
        TextField cardNumberField = new TextField();
        cardNumberField.setPromptText("Numéro de carte (16 chiffres fictifs)");
        cardNumberField.setStyle(inputStyle());
        cardNumberField.setTextFormatter(
                new TextFormatter<>(
                        c -> {
                            String t = c.getControlNewText().replaceAll("\\D", "");
                            return t.length() <= 16 ? c : null;
                        }));
        TextField expMm = new TextField();
        expMm.setPromptText("MM");
        expMm.setPrefWidth(56);
        expMm.setStyle(inputStyle());
        expMm.setTextFormatter(
                new TextFormatter<>(c -> c.getControlNewText().replaceAll("\\D", "").length() <= 2 ? c : null));
        TextField expYy = new TextField();
        expYy.setPromptText("AA");
        expYy.setPrefWidth(56);
        expYy.setStyle(inputStyle());
        expYy.setTextFormatter(
                new TextFormatter<>(c -> c.getControlNewText().replaceAll("\\D", "").length() <= 2 ? c : null));
        TextField cvvField = new TextField();
        cvvField.setPromptText("CVV (non enregistré)");
        cvvField.setPrefWidth(100);
        cvvField.setStyle(inputStyle());
        cvvField.setTextFormatter(
                new TextFormatter<>(c -> c.getControlNewText().replaceAll("\\D", "").length() <= 4 ? c : null));
        TextField brandField = new TextField("Visa");
        brandField.setPromptText("Réseau (Visa, Mastercard…)");
        brandField.setStyle(inputStyle());
        HBox expRow = new HBox(8, expMm, expYy, cvvField);
        expRow.setAlignment(Pos.CENTER_LEFT);
        VBox cardPane = new VBox(10, cardHero, holderField, cardNumberField, expRow, brandField);

        TextField paypalCode = new TextField();
        paypalCode.setPromptText("Code / référence PayPal (simulation, ex. PAYPAL-TEST-889912)");
        paypalCode.setStyle(inputStyle());
        Region ppFace = new Region();
        ppFace.setPrefHeight(72);
        ppFace.setStyle(
                "-fx-background-radius: 14; -fx-background-color: linear-gradient(to right, #003087, #009cde);");
        Label ppLbl = new Label("PayPal (modèle)");
        ppLbl.setTextFill(Color.WHITE);
        ppLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        StackPane ppHero = new StackPane(ppFace, ppLbl);
        VBox paypalPane = new VBox(10, ppHero, paypalCode);

        TextField walletAlias = new TextField();
        walletAlias.setPromptText("Nom du portefeuille / alias");
        walletAlias.setStyle(inputStyle());
        Region wlFace = new Region();
        wlFace.setPrefHeight(72);
        wlFace.setStyle(
                "-fx-background-radius: 14; -fx-background-color: linear-gradient(to right, #312e81, #4c1d95);");
        Label wlLbl = new Label("Wallet ChriOnline (modèle)");
        wlLbl.setTextFill(Color.web("#E0E7FF"));
        wlLbl.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        StackPane wlHero = new StackPane(wlFace, wlLbl);
        VBox walletPane = new VBox(10, wlHero, walletAlias);

        Label deliveryLbl =
                new Label(
                        "Paiement à la livraison : aucune carte ni coordonnée bancaire n’est saisie ici.\n"
                                + "Le montant sera réglé au livreur (simulation).");
        deliveryLbl.setTextFill(Color.web("#CBD5E1"));
        deliveryLbl.setWrapText(true);
        VBox deliveryPane = new VBox(8, deliveryLbl);

        StackPane templateStack = new StackPane();
        templateStack.setMinHeight(200);

        CheckBox saveChk =
                new CheckBox(
                        "Mémoriser ce moyen pour les prochains achats (masqué : 4 derniers chiffres de carte,"
                                + " code PayPal tronqué, alias wallet — jamais le CVV).");
        saveChk.setTextFill(Color.web("#E2E8F0"));
        saveChk.setWrapText(true);

        Runnable updateTemplate =
                () -> {
                    String sel = typeBox.getSelectionModel().getSelectedItem();
                    typeHint.setText(sel != null ? paymentHelp(sel) : "");
                    TypePaiement tp;
                    try {
                        tp = TypePaiement.valueOf(sel);
                    } catch (Exception e) {
                        tp = TypePaiement.CARTE_BANCAIRE;
                    }
                    templateStack.getChildren().clear();
                    switch (tp) {
                        case PAYPAL:
                            templateStack.getChildren().add(paypalPane);
                            break;
                        case WALLET:
                            templateStack.getChildren().add(walletPane);
                            break;
                        case A_LA_LIVRAISON:
                            templateStack.getChildren().add(deliveryPane);
                            break;
                        default:
                            templateStack.getChildren().add(cardPane);
                            break;
                    }
                    boolean canSave = tp != TypePaiement.A_LA_LIVRAISON;
                    saveChk.setVisible(canSave);
                    saveChk.setManaged(canSave);
                    if (!canSave) {
                        saveChk.setSelected(false);
                    }
                };
        typeBox.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> updateTemplate.run());
        updateTemplate.run();

        Runnable fillPaymentTestTemplate =
                () -> {
                    String sel = typeBox.getSelectionModel().getSelectedItem();
                    TypePaiement tp;
                    try {
                        tp = TypePaiement.valueOf(sel);
                    } catch (Exception e) {
                        tp = TypePaiement.CARTE_BANCAIRE;
                    }
                    switch (tp) {
                        case PAYPAL:
                            paypalCode.setText("PAYPAL-TEST-889912");
                            break;
                        case WALLET:
                            walletAlias.setText("DEMO_WALLET_01");
                            break;
                        case A_LA_LIVRAISON:
                            break;
                        default:
                            holderField.setText("Client Démo");
                            cardNumberField.setText("4242424242424242");
                            expMm.setText("12");
                            expYy.setText("28");
                            cvvField.setText("123");
                            brandField.setText("Visa");
                            break;
                    }
                };
        Button fillTplBtn = createOutlineButton("Remplir modèle test (tous modes)");
        fillTplBtn.setMaxWidth(320);
        fillTplBtn.setOnAction(e -> fillPaymentTestTemplate.run());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(14));
        int r = 0;
        Label lCmd = new Label("N° commande");
        lCmd.setTextFill(Color.web("#CBD5E1"));
        grid.add(lCmd, 0, r);
        grid.add(cmdField, 1, r++);
        Label lType = new Label("Mode de paiement");
        lType.setTextFill(Color.web("#CBD5E1"));
        grid.add(lType, 0, r);
        grid.add(typeBox, 1, r++);
        grid.add(new Label(""), 0, r);
        grid.add(typeHint, 1, r++);
        Label lTpl = new Label("Saisie");
        lTpl.setTextFill(Color.web("#CBD5E1"));
        grid.add(lTpl, 0, r);
        grid.add(templateStack, 1, r++);
        Label lCoup = new Label("Coupon");
        lCoup.setTextFill(Color.web("#CBD5E1"));
        grid.add(lCoup, 0, r);
        grid.add(couponField, 1, r++);
        grid.add(new Label(""), 0, r);
        grid.add(fillTplBtn, 1, r++);
        grid.add(new Label(""), 0, r);
        grid.add(saveChk, 1, r);

        dlg.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        parseUserId();
        String cid = cmdField.getText().trim();
        if (cid.isEmpty()) {
            alert(Alert.AlertType.WARNING, "ID commande requis.");
            return;
        }
        String type = typeBox.getSelectionModel().getSelectedItem();
        String coupon = couponField.getText() != null ? couponField.getText().trim() : "";
        String lastFour = paymentLastFourDigits(cardNumberField.getText());
        String payJson =
                "{\"commandeId\":\""
                        + jsonEsc(cid)
                        + "\",\"userId\":\""
                        + userId
                        + "\",\"typePaiement\":\""
                        + jsonEsc(type)
                        + "\",\"coupon\":\""
                        + jsonEsc(coupon)
                        + "\",\"saveTemplate\":\""
                        + (saveChk.isSelected() ? "true" : "false")
                        + "\",\"holderName\":\""
                        + jsonEsc(holderField.getText().trim())
                        + "\",\"lastFour\":\""
                        + jsonEsc(lastFour)
                        + "\",\"brand\":\""
                        + jsonEsc(brandField.getText().trim())
                        + "\",\"expMonth\":\""
                        + jsonEsc(expMm.getText().trim())
                        + "\",\"expYear\":\""
                        + jsonEsc(expYy.getText().trim())
                        + "\",\"paypalCode\":\""
                        + jsonEsc(paypalCode.getText().trim())
                        + "\",\"walletAlias\":\""
                        + jsonEsc(walletAlias.getText().trim())
                        + "\"}";

        runAsync(() -> {
            try {
                Message res = api.send(Message.request("SIMULATE_PAYMENT", "11", payJson));
                Platform.runLater(() -> {
                    if ("SUCCESS".equals(res.getStatus())) {
                        String summary = SocketApiClient.formatPaymentSummaryForUser(res.getPayload());
                        lastInvoiceText = summary;
                        alert(Alert.AlertType.INFORMATION, summary);
                        Integer fraud = SocketApiClient.parsePaymentFraudScore(res.getPayload());
                        if (lastPaymentRiskLabel != null) {
                            lastPaymentRiskLabel.setText(
                                    fraud != null
                                            ? ("Indicateur de risque (simulation) : " + fraud + " %")
                                            : "Indicateur de risque : voir résumé ci-dessus");
                        }
                    } else {
                        alertError(res.getErrorCode());
                    }
                    refreshCommandes();
                    refreshSavedPaymentMethods();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> alertThrowable(ex));
            }
        });
    }

    private static String buildPaymentMethodsText() {
        StringBuilder sb = new StringBuilder();
        for (TypePaiement t : TypePaiement.values()) {
            sb.append("- ").append(t.name()).append(" - ").append(paymentHelp(t.name())).append("\n");
        }
        return sb.toString().trim();
    }

    private static String paymentHelp(String typeName) {
        try {
            return paymentHelp(TypePaiement.valueOf(typeName));
        } catch (Exception e) {
            return "";
        }
    }

    private static String paymentHelp(TypePaiement t) {
        switch (t) {
            case CARTE_BANCAIRE:
                return "Carte bancaire (simulation risque / file d'attente).";
            case PAYPAL:
                return "PayPal — saisir un code / référence de simulation (pas d’e-mail).";
            case STRIPE:
                return "Paiement Stripe (carte).";
            case A_LA_LIVRAISON:
                return "Paiement \u00e0 la livraison (pas de pr\u00e9-d\u00e9bit).";
            case WALLET:
                return "Portefeuille \u00e9lectronique interne.";
            case PAIEMENT_2X:
                return "Paiement en 2 fois.";
            case PAIEMENT_3X:
                return "Paiement en 3 fois.";
            default:
                return "";
        }
    }

    private void sendAccountEmailVerification() {
        if (!accountLoggedIn || userId <= 0) {
            alert(Alert.AlertType.WARNING, "Connectez-vous d’abord.");
            return;
        }
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(
                                        Message.request(
                                                "EMAIL_VERIFY_SEND",
                                                "evs",
                                                "{\"userId\":\"" + userId + "\"}"));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        String p = res.getPayload();
                                        if (p != null && p.contains("\"alreadyVerified\":true")) {
                                            sessionEmailVerified = true;
                                            persistSession();
                                            refreshAccountLabels();
                                            alert(
                                                    Alert.AlertType.INFORMATION,
                                                    "Votre adresse e-mail est déjà vérifiée.");
                                        } else {
                                            alert(
                                                    Alert.AlertType.INFORMATION,
                                                    "Un code a été envoyé à votre adresse (voir la console du"
                                                            + " serveur si le SMTP n’est pas configuré).");
                                        }
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void confirmAccountEmailVerification() {
        if (!accountLoggedIn || userId <= 0) {
            return;
        }
        String code =
                accountEmailVerifyCodeField != null ? accountEmailVerifyCodeField.getText().trim() : "";
        if (code.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Saisissez le code reçu par e-mail.");
            return;
        }
        String payload =
                "{\"userId\":\""
                        + userId
                        + "\",\"code\":\""
                        + jsonEsc(code)
                        + "\"}";
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(Message.request("EMAIL_VERIFY_CONFIRM", "evc", payload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        sessionEmailVerified = true;
                                        persistSession();
                                        refreshAccountLabels();
                                        if (accountEmailVerifyCodeField != null) {
                                            accountEmailVerifyCodeField.clear();
                                        }
                                        alert(Alert.AlertType.INFORMATION, "Adresse e-mail vérifiée.");
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void sendProfileSecurityOtp() {
        if (!accountLoggedIn || userId <= 0) {
            return;
        }
        runAsync(
                () -> {
                    try {
                        Message res =
                                api.send(
                                        Message.request(
                                                "PROFILE_OTP_SEND",
                                                "potp",
                                                "{\"userId\":\"" + userId + "\"}"));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        alert(
                                                Alert.AlertType.INFORMATION,
                                                "Si votre e-mail est vérifié, un code vous a été envoyé (voir aussi"
                                                        + " la console du serveur).");
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void openForgotPasswordDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(primaryStage);
        dlg.setTitle("Mot de passe oublié");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        int r = 0;
        TextField email = new TextField();
        email.setPromptText("Adresse e-mail");
        TextField phone = new TextField();
        phone.setPromptText("Ou numéro de téléphone (chiffres)");
        Label hint1 =
                new Label(
                        "Indiquez l’e-mail ou le téléphone associé au compte. Par e-mail : l’adresse doit être"
                                + " vérifiée ; le code est envoyé par e-mail (ou affiché en console si SMTP est"
                                + " désactivé). Par téléphone : indice masqué ci-dessous."
                );
        hint1.setWrapText(true);
        hint1.setMaxWidth(420);
        hint1.setTextFill(Color.web("#64748b"));
        hint1.setFont(Font.font(11));
        Label maskedHint = new Label("");
        maskedHint.setWrapText(true);
        maskedHint.setMaxWidth(420);
        maskedHint.setTextFill(Color.web("#a5b4fc"));
        maskedHint.setFont(Font.font(11));
        Button sendCode = createOutlineButton("Envoyer un code");
        Separator sep = new Separator();
        TextField codeField = new TextField();
        codeField.setPromptText("Code reçu");
        PasswordField newPw = new PasswordField();
        newPw.setPromptText("Nouveau mot de passe");
        Button apply = createGlowButton("Enregistrer le nouveau mot de passe", "#2962FF");

        grid.add(hint1, 0, r, 2, 1);
        r++;
        grid.add(maskedHint, 0, r, 2, 1);
        r++;
        grid.add(new Label("E-mail"), 0, r);
        grid.add(email, 1, r++);
        grid.add(new Label("Téléphone"), 0, r);
        grid.add(phone, 1, r++);
        grid.add(sendCode, 1, r++);
        grid.add(sep, 0, r, 2, 1);
        r++;
        grid.add(new Label("Code"), 0, r);
        grid.add(codeField, 1, r++);
        grid.add(new Label("Nouveau mot de passe"), 0, r);
        grid.add(newPw, 1, r++);
        grid.add(apply, 1, r);

        sendCode.setOnAction(
                e -> {
                    String em = email.getText() != null ? email.getText().trim() : "";
                    String ph = phone.getText() != null ? phone.getText().trim() : "";
                    if (em.isEmpty() == ph.isEmpty()) {
                        alert(Alert.AlertType.WARNING, "Renseignez soit l'e-mail, soit le téléphone.");
                        return;
                    }
                    String payload;
                    if (!em.isEmpty()) {
                        payload = "{\"email\":\"" + jsonEsc(em) + "\"}";
                    } else {
                        payload = "{\"phone\":\"" + jsonEsc(ph.replaceAll("\\D+", "")) + "\"}";
                    }
                    runAsync(
                            () -> {
                                try {
                                    Message res = api.send(Message.request("FORGOT_PASSWORD", "20", payload));
                                    Platform.runLater(
                                            () -> {
                                                if ("SUCCESS".equals(res.getStatus())) {
                                                    String pl = res.getPayload();
                                                    String me =
                                                            SocketApiClient.extractJsonStringValue(
                                                                    pl, "maskedEmail");
                                                    String mp =
                                                            SocketApiClient.extractJsonStringValue(
                                                                    pl, "maskedPhone");
                                                    if (!me.isEmpty()) {
                                                        maskedHint.setText("Indice e-mail : " + me);
                                                    } else if (!mp.isEmpty()) {
                                                        maskedHint.setText("Indice téléphone : " + mp);
                                                    } else {
                                                        maskedHint.setText("");
                                                    }
                                                    alert(
                                                            Alert.AlertType.INFORMATION,
                                                            "Si un compte correspond, un code a été généré. "
                                                                    + "Consultez votre boîte mail ou la console du serveur."
                                                    );
                                                } else if ("EMAIL_NOT_VERIFIED".equals(res.getErrorCode())) {
                                                    alert(
                                                            Alert.AlertType.WARNING,
                                                            UiMessages.errorCode("EMAIL_NOT_VERIFIED"));
                                                } else {
                                                    alertError(res.getErrorCode());
                                                }
                                            });
                                } catch (Exception ex) {
                                    Platform.runLater(() -> alertThrowable(ex));
                                }
                            });
                });

        apply.setOnAction(
                e -> {
                    String em = email.getText() != null ? email.getText().trim() : "";
                    String ph = phone.getText() != null ? phone.getText().trim() : "";
                    String code = codeField.getText() != null ? codeField.getText().trim() : "";
                    String npw = newPw.getText() != null ? newPw.getText() : "";
                    if (em.isEmpty() == ph.isEmpty() || code.isEmpty() || npw.length() < 4) {
                        alert(
                                Alert.AlertType.WARNING,
                                "Renseignez le même e-mail ou téléphone que pour le code, le code et un mot de passe (4 caractères minimum)."
                        );
                        return;
                    }
                    String payload;
                    if (!em.isEmpty()) {
                        payload =
                                "{\"email\":\""
                                        + jsonEsc(em)
                                        + "\",\"code\":\""
                                        + jsonEsc(code)
                                        + "\",\"newPassword\":\""
                                        + jsonEsc(npw)
                                        + "\"}";
                    } else {
                        payload =
                                "{\"phone\":\""
                                        + jsonEsc(ph.replaceAll("\\D+", ""))
                                        + "\",\"code\":\""
                                        + jsonEsc(code)
                                        + "\",\"newPassword\":\""
                                        + jsonEsc(npw)
                                        + "\"}";
                    }
                    runAsync(
                            () -> {
                                try {
                                    Message res = api.send(Message.request("RESET_PASSWORD", "21", payload));
                                    Platform.runLater(
                                            () -> {
                                                if ("SUCCESS".equals(res.getStatus())) {
                                                    alert(
                                                            Alert.AlertType.INFORMATION,
                                                            "Mot de passe mis à jour. Vous pouvez vous connecter."
                                                    );
                                                    dlg.close();
                                                } else {
                                                    alertError(res.getErrorCode());
                                                }
                                            });
                                } catch (Exception ex) {
                                    Platform.runLater(() -> alertThrowable(ex));
                                }
                            });
                });

        dlg.getDialogPane().setContent(grid);
        dlg.showAndWait();
    }

    /** Connexion au compte via les champs latéraux (même logique socket que l’ancienne boîte « Connexion »). */
    private void submitSidebarLogin() {
        if (failedLoginAttemptsThisSession >= MAX_LOGIN_ATTEMPTS_PER_SESSION) {
            if (sidebarLoginBtn != null) {
                sidebarLoginBtn.setDisable(true);
            }
            alert(
                    Alert.AlertType.WARNING,
                    "Trop de tentatives de connexion (" + MAX_LOGIN_ATTEMPTS_PER_SESSION + "). Redémarrez l’application pour réessayer.");
            return;
        }
        String em = sidebarLoginEmail != null ? sidebarLoginEmail.getText().trim() : "";
        String pw = sidebarLoginPassword != null ? sidebarLoginPassword.getText() : "";
        if (em.isEmpty() || pw.isEmpty()) {
            alert(Alert.AlertType.WARNING, "E-mail (ou téléphone) et mot de passe requis.");
            return;
        }
        String payload = "{\"email\":\"" + jsonEsc(em) + "\",\"password\":\"" + jsonEsc(pw) + "\"}";
        rebuildApiFromFields();
        if (host.isBlank()) {
            alert(
                    Alert.AlertType.WARNING,
                    "Indiquez l’adresse du serveur (champ Hôte), puis cliquez « Connecter » avant de vous"
                            + " identifier.");
            return;
        }
        runAsync(
                () -> {
                    try {
                        Message res = api.send(Message.request("LOGIN", "2", payload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        failedLoginAttemptsThisSession = 0;
                                        if (sidebarLoginBtn != null) {
                                            sidebarLoginBtn.setDisable(false);
                                        }
                                        applySession(res.getPayload());
                                        if (sidebarLoginPassword != null) {
                                            sidebarLoginPassword.clear();
                                        }
                                        showAppToast("Connexion réussie.", Alert.AlertType.INFORMATION);
                                    } else {
                                        failedLoginAttemptsThisSession++;
                                        if (failedLoginAttemptsThisSession >= MAX_LOGIN_ATTEMPTS_PER_SESSION
                                                && sidebarLoginBtn != null) {
                                            sidebarLoginBtn.setDisable(true);
                                        }
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(
                                () -> {
                                    failedLoginAttemptsThisSession++;
                                    if (failedLoginAttemptsThisSession >= MAX_LOGIN_ATTEMPTS_PER_SESSION
                                            && sidebarLoginBtn != null) {
                                        sidebarLoginBtn.setDisable(true);
                                    }
                                    alertThrowable(ex);
                                });
                    }
                });
    }

    private void openAdminRsaLoginDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(primaryStage);
        dlg.setTitle("Connexion admin (RSA)");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField email = new TextField();
        email.setPromptText("E-mail admin (doit être rôle ADMIN)");

        TextField keyPath = new TextField();
        keyPath.setEditable(false);
        keyPath.setPromptText("Fichier clé privée PEM (PKCS8)");

        Button browse = new Button("Choisir…");
        browse.setOnAction(
                e -> {
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Sélectionner la clé privée PEM");
                    fc.getExtensionFilters()
                            .addAll(
                                    new FileChooser.ExtensionFilter("PEM / key", "*.pem", "*.key", "*.txt"),
                                    new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
                    var f = fc.showOpenDialog(primaryStage);
                    if (f != null) {
                        keyPath.setText(f.getAbsolutePath());
                    }
                });

        int r = 0;
        grid.add(new Label("Email"), 0, r);
        grid.add(email, 1, r++);
        grid.add(new Label("Clé privée"), 0, r);
        grid.add(new HBox(8, keyPath, browse), 1, r++);

        Label hint =
                new Label(
                        "Le serveur envoie un challenge unique, puis vérifie la signature RSA avec la clé publique stockée en base.");
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#64748B"));
        hint.setFont(Font.font(10));
        grid.add(hint, 0, r, 2, 1);

        dlg.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        String em = email.getText() != null ? email.getText().trim() : "";
        String kp = keyPath.getText() != null ? keyPath.getText().trim() : "";
        if (em.isEmpty() || kp.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Email et fichier de clé privée requis.");
            return;
        }
        rebuildApiFromFields();
        if (host.isBlank()) {
            alert(Alert.AlertType.WARNING, "Indiquez l’adresse du serveur (Hôte) puis « Connecter ».");
            return;
        }

        runAsync(
                () -> {
                    try {
                        String pem = Files.readString(Path.of(kp), StandardCharsets.UTF_8);
                        PrivateKey privateKey = PemKeyUtil.parsePkcs8PrivateKeyPem(pem);

                        String reqPayload = "{\"email\":\"" + jsonEsc(em) + "\"}";
                        Message ch =
                                api.send(Message.request("ADMIN_CHALLENGE_REQUEST", "31", reqPayload));
                        if (!"SUCCESS".equals(ch.getStatus())) {
                            Platform.runLater(() -> alertError(ch.getErrorCode()));
                            return;
                        }
                        Map<String, String> m = JsonUtil.toMap(ch.getPayload());
                        String challengeId = m.get("challengeId");
                        String challenge = m.get("challenge");
                        if (challengeId == null || challenge == null) {
                            Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Réponse challenge invalide."));
                            return;
                        }
                        byte[] sig = RsaSignatureUtil.signChallenge(challenge, privateKey);
                        String sigB64 = Base64.getEncoder().encodeToString(sig);
                        String verifyPayload =
                                "{\"challengeId\":\""
                                        + jsonEsc(challengeId)
                                        + "\",\"signatureB64\":\""
                                        + jsonEsc(sigB64)
                                        + "\"}";
                        Message res =
                                api.send(Message.request("ADMIN_CHALLENGE_VERIFY", "32", verifyPayload));
                        Platform.runLater(
                                () -> {
                                    if ("SUCCESS".equals(res.getStatus())) {
                                        applySession(res.getPayload());
                                        if (sidebarLoginPassword != null) {
                                            sidebarLoginPassword.clear();
                                        }
                                        showAppToast("Connexion admin réussie.", Alert.AlertType.INFORMATION);
                                    } else {
                                        alertError(res.getErrorCode());
                                    }
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> alertThrowable(ex));
                    }
                });
    }

    private void openRegisterDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(primaryStage);
        dlg.setTitle("Inscription");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField username = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        phone.setPromptText("Téléphone (chiffres)");
        PasswordField password = new PasswordField();

        int r = 0;
        grid.add(new Label("Nom d'utilisateur"), 0, r);
        grid.add(username, 1, r++);
        grid.add(new Label("Email"), 0, r);
        grid.add(email, 1, r++);
        grid.add(new Label("Téléphone"), 0, r);
        grid.add(phone, 1, r++);
        grid.add(new Label("Mot de passe"), 0, r);
        grid.add(password, 1, r++);
        dlg.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        String u = username.getText() != null ? username.getText().trim() : "";
        String em = email.getText() != null ? email.getText().trim() : "";
        String ph = phone.getText() != null ? phone.getText().trim() : "";
        String pw = password.getText() != null ? password.getText() : "";
        if (u.isEmpty() || em.isEmpty() || ph.isEmpty() || pw.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Tous les champs sont requis.");
            return;
        }
        String payload =
                "{\"username\":\"" + jsonEsc(u) + "\",\"email\":\"" + jsonEsc(em) + "\",\"phone\":\"" + jsonEsc(ph)
                        + "\",\"password\":\"" + jsonEsc(pw) + "\"}";
        rebuildApiFromFields();
        if (host.isBlank()) {
            alert(
                    Alert.AlertType.WARNING,
                    "Indiquez l’adresse du serveur (Hôte) pour joindre la base des comptes, puis réessayez"
                            + " l’inscription.");
            return;
        }
        runAsync(() -> {
            try {
                Message res = api.send(Message.request("REGISTER", "3", payload));
                Platform.runLater(() -> {
                    if ("SUCCESS".equals(res.getStatus())) {
                        applySession(res.getPayload());
                        alert(Alert.AlertType.INFORMATION, "Compte créé. Bienvenue " + u + " !");
                    } else {
                        alertError(res.getErrorCode());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> alertThrowable(ex));
            }
        });
    }

    private void applySession(String jsonPayload) {
        try {
            Integer id = SocketApiClient.parseAuthUserId(jsonPayload);
            String name = SocketApiClient.parseAuthUsername(jsonPayload);
            if (id != null) {
                userId = id;
                if (userIdField != null) {
                    userIdField.setText(String.valueOf(id));
                }
            }
            String newTok = SocketApiClient.parseAuthSessionToken(jsonPayload);
            if (newTok != null && !newTok.isEmpty()) {
                sessionToken = newTok;
            }
            sessionRole = SocketApiClient.parseAuthRole(jsonPayload);
            sessionUsername = name != null ? name : "";
            sessionEmail = SocketApiClient.parseAuthEmail(jsonPayload);
            sessionPhone = SocketApiClient.parseAuthPhone(jsonPayload);
            Boolean ev = SocketApiClient.parseAuthEmailVerified(jsonPayload);
            if (ev != null) {
                sessionEmailVerified = ev;
            }
            setAccountLoggedInUi(true);
            updateSessionRoleNav();
            refreshSessionBanner();
            persistSession();
            refreshAccountLabels();
            refreshSavedPaymentMethods();
        } catch (Exception ex) {
            alertThrowable(ex);
            showAppToast(
                    "Connexion reçue mais erreur d’affichage du compte. Réessayez ou redémarrez l’app.",
                    Alert.AlertType.ERROR);
        }
    }

    private void persistSession() {
        if (!accountLoggedIn || userId <= 0) {
            return;
        }
        ClientPrefs.putBoolean(PREF_SESSION, "active", true);
        ClientPrefs.putInt(PREF_SESSION, "userId", userId);
        ClientPrefs.putString(PREF_SESSION, "sessionToken", sessionToken != null ? sessionToken : "");
        ClientPrefs.putString(PREF_SESSION, "role", sessionRole != null ? sessionRole : "CLIENT");
        ClientPrefs.putString(PREF_SESSION, "username", sessionUsername != null ? sessionUsername : "");
        ClientPrefs.putString(PREF_SESSION, "email", sessionEmail != null ? sessionEmail : "");
        ClientPrefs.putString(PREF_SESSION, "phone", sessionPhone != null ? sessionPhone : "");
        ClientPrefs.putBoolean(PREF_SESSION, "emailVerified", sessionEmailVerified);
    }

    private void clearPersistedSession() {
        ClientPrefs.remove(PREF_SESSION, "active");
        ClientPrefs.remove(PREF_SESSION, "userId");
        ClientPrefs.remove(PREF_SESSION, "sessionToken");
        ClientPrefs.remove(PREF_SESSION, "role");
        ClientPrefs.remove(PREF_SESSION, "username");
        ClientPrefs.remove(PREF_SESSION, "email");
        ClientPrefs.remove(PREF_SESSION, "phone");
        ClientPrefs.remove(PREF_SESSION, "emailVerified");
    }

    /** Restaure l'affichage « connecté » sans renvoyer le mot de passe (session locale uniquement). */
    private void restorePersistedSession() {
        if (!ClientPrefs.getBoolean(PREF_SESSION, "active", false)) {
            return;
        }
        String tok = ClientPrefs.getString(PREF_SESSION, "sessionToken", "");
        if (tok == null || tok.isBlank()) {
            clearPersistedSession();
            return;
        }
        int id = ClientPrefs.getInt(PREF_SESSION, "userId", -1);
        if (id <= 0) {
            return;
        }
        userId = id;
        sessionToken = tok;
        sessionRole = ClientPrefs.getString(PREF_SESSION, "role", "CLIENT");
        sessionUsername = ClientPrefs.getString(PREF_SESSION, "username", "");
        sessionEmail = ClientPrefs.getString(PREF_SESSION, "email", "");
        sessionPhone = ClientPrefs.getString(PREF_SESSION, "phone", "");
        sessionEmailVerified = ClientPrefs.getBoolean(PREF_SESSION, "emailVerified", false);
        if (userIdField != null) {
            userIdField.setText(String.valueOf(userId));
        }
        setAccountLoggedInUi(true);
        updateSessionRoleNav();
        refreshSessionBanner();
        refreshAccountLabels();
        refreshSavedPaymentMethods();
    }

    private static void runAsync(Runnable r) {
        new Thread(r, "socket-ui").start();
    }

    private void alert(Alert.AlertType type, String msg) {
        showAppToast(msg != null ? msg : "", type);
    }

    /** Themed bottom toast — no system « Message » dialog. */
    private void showAppToast(String message, Alert.AlertType type) {
        if (toastOverlay == null) {
            return;
        }
        if (currentToastAnimation != null) {
            currentToastAnimation.stop();
            currentToastAnimation = null;
        }
        Platform.runLater(
                () -> {
                    toastOverlay.setOpacity(1);
                    String bg;
                    String border;
                    switch (type) {
                        case ERROR -> {
                            bg = "rgba(88,28,28,0.96)";
                            border = "rgba(252,165,165,0.55)";
                        }
                        case WARNING -> {
                            bg = "rgba(113,63,18,0.96)";
                            border = "rgba(253,224,71,0.45)";
                        }
                        default -> {
                            bg = "rgba(15,23,42,0.97)";
                            border = "rgba(96,165,250,0.5)";
                        }
                    }
                    Label lbl = new Label(message);
                    lbl.setWrapText(true);
                    lbl.setMaxWidth(440);
                    lbl.setTextFill(Color.web("#f8fafc"));
                    lbl.setFont(Font.font("Arial", FontWeight.NORMAL, 14));

                    VBox box = new VBox(6, lbl);
                    box.setPadding(new Insets(18, 22, 18, 22));
                    box.setStyle(
                            "-fx-background-color: "
                                    + bg
                                    + ";"
                                    + "-fx-border-color: "
                                    + border
                                    + ";"
                                    + "-fx-border-radius: 16;"
                                    + "-fx-background-radius: 16;"
                                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 28, 0, 0, 4);");

                    toastOverlay.getChildren().setAll(box);
                    StackPane.setAlignment(box, Pos.BOTTOM_CENTER);
                    StackPane.setMargin(box, new Insets(0, 28, 56, 28));
                    toastOverlay.setVisible(true);
                    toastOverlay.setOpacity(1);
                    toastOverlay.setMouseTransparent(false);

                    double seconds =
                            type == Alert.AlertType.ERROR ? 5.2 : (type == Alert.AlertType.WARNING ? 4.2 : 3.6);
                    PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(380), toastOverlay);
                    fadeOut.setFromValue(1);
                    fadeOut.setToValue(0);
                    SequentialTransition seq = new SequentialTransition(pause, fadeOut);
                    seq.setOnFinished(
                            e -> {
                                toastOverlay.getChildren().clear();
                                toastOverlay.setVisible(false);
                                toastOverlay.setMouseTransparent(true);
                                toastOverlay.setOpacity(1);
                                currentToastAnimation = null;
                            });
                    currentToastAnimation = seq;
                    seq.play();
                });
    }

    private void alertError(String code) {
        alert(Alert.AlertType.ERROR, UiMessages.errorCode(code));
    }

    private void alertAdminProductCreateFailure(Message res) {
        String code = res != null ? res.getErrorCode() : "";
        String msg = UiMessages.errorCode(code);
        String detail = res != null ? res.getPayload() : "";
        if (detail != null && !detail.isBlank()) {
            msg = msg + "\n\nDetail technique admin: " + detail;
        }
        alert(Alert.AlertType.ERROR, msg);
    }

    private void alertThrowable(Exception ex) {
        ex.printStackTrace();
        String detail = ex.getMessage() != null ? "\n\nDetail technique: " + ex.getMessage() : "";
        alert(Alert.AlertType.ERROR, UiMessages.networkFailure() + detail);
    }

    private Button createNavButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(40);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        btn.setTextFill(Color.WHITE);
        btn.setOnAction(e -> action.run());
        addButtonHover(btn, 1.03);
        return btn;
    }

    private Label createSectionLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web("#7183A8"));
        label.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        return label;
    }

    private Button createGlowButton(String text, String color) {
        Button btn = new Button(text);
        btn.setTextFill(Color.WHITE);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        btn.setStyle("-fx-background-radius: 14; -fx-background-color: " + color + "; -fx-padding: 9 16 9 16;");
        DropShadow glow = new DropShadow();
        glow.setRadius(16);
        glow.setColor(Color.web(color, 0.35));
        btn.setEffect(glow);
        addButtonHover(btn, 1.04);
        return btn;
    }

    private Button createOutlineButton(String text) {
        Button btn = new Button(text);
        btn.setTextFill(Color.WHITE);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        btn.setStyle(
                "-fx-background-radius: 14; -fx-background-color: rgba(255,255,255,0.06); "
                        + "-fx-border-color: rgba(255,255,255,0.10); -fx-border-radius: 14; -fx-padding: 9 16 9 16;"
        );
        addButtonHover(btn, 1.03);
        return btn;
    }

    private VBox createGlassBox(double spacing, double padding) {
        VBox box = new VBox(spacing);
        box.setPadding(new Insets(padding));
        box.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-border-color: rgba(255,255,255,0.10); "
                        + "-fx-background-radius: 20; -fx-border-radius: 20;"
        );
        box.setEffect(new DropShadow(18, Color.web("#000000", 0.24)));
        return box;
    }

    private HBox createGlassHBox(double spacing) {
        HBox box = new HBox(spacing);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-border-color: rgba(255,255,255,0.10); "
                        + "-fx-background-radius: 20; -fx-border-radius: 20;"
        );
        box.setEffect(new DropShadow(14, Color.web("#000000", 0.2)));
        return box;
    }

    private String inputStyle() {
        return "-fx-background-color: rgba(18,24,42,0.92); -fx-border-color: rgba(255,255,255,0.12); "
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-text-fill: #f1f5f9; "
                + "-fx-prompt-text-fill: #7283A7; -fx-padding: 8 12 8 12;";
    }

    private String searchFieldInsideStyle() {
        return "-fx-background-color: rgba(18,24,42,0.55); -fx-text-fill: #f1f5f9; "
                + "-fx-prompt-text-fill: #7283A7; -fx-font-size: 14px; "
                + "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius: 10; -fx-background-radius: 10; "
                + "-fx-padding: 6 8 6 8;";
    }

    private String comboBoxDarkStyle() {
        return "-fx-background-color: rgba(18,24,42,0.92); -fx-border-color: rgba(255,255,255,0.12); "
                + "-fx-border-radius: 12; -fx-background-radius: 12; -fx-text-fill: white; "
                + "-fx-mark-color: white;";
    }

    private String textAreaDarkStyle() {
        return "-fx-control-inner-background: rgba(12,16,30,0.95); "
                + "-fx-background-color: rgba(12,16,30,0.95); "
                + "-fx-text-fill: #C8D4EE; -fx-font-size: 11px; "
                + "-fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 12; -fx-background-radius: 12; "
                + "-fx-padding: 8;";
    }

    private void applyDarkListView(ListView<String> lv) {
        lv.setStyle(
                "-fx-background-color: rgba(12,16,30,0.95);"
                        + "-fx-control-inner-background: rgba(12,16,30,0.98);"
                        + "-fx-border-color: rgba(255,255,255,0.1);"
                        + "-fx-border-radius: 14;"
                        + "-fx-background-radius: 14;"
                        + "-fx-padding: 4;"
        );
        lv.setCellFactory(
                col ->
                        new ListCell<String>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setStyle("-fx-background-color: rgba(12,16,30,0.98);");
                                } else {
                                    setText(item);
                                    setTextFill(Color.web("#E2E8F0"));
                                    setWrapText(true);
                                    setStyle(
                                            "-fx-background-color: rgba(255,255,255,0.05);"
                                                    + "-fx-background-radius: 8;"
                                                    + "-fx-padding: 8 10 8 10;"
                                    );
                                }
                            }
                        });
    }

    /** Favoris : lignes = id produit, libellé = nom ; clic ouvre la fiche. */
    private void applyFavoritesListView(ListView<String> lv) {
        lv.setFixedCellSize(34);
        lv.setStyle(
                "-fx-background-color: rgba(12,16,30,0.98);"
                        + "-fx-control-inner-background: rgba(12,16,30,0.98);"
                        + "-fx-border-color: rgba(255,255,255,0.12);"
                        + "-fx-border-radius: 14;"
                        + "-fx-background-radius: 14;"
                        + "-fx-padding: 2;"
        );
        lv.setCellFactory(
                col ->
                        new ListCell<String>() {
                            {
                                setOnMouseClicked(
                                        e -> {
                                            String id = getItem();
                                            if (id == null) {
                                                return;
                                            }
                                            Product p = findProduct(id);
                                            if (p != null) {
                                                showProductDetail(p);
                                            }
                                        });
                            }

                            @Override
                            protected void updateItem(String productId, boolean empty) {
                                super.updateItem(productId, empty);
                                if (empty || productId == null) {
                                    setText(null);
                                    setGraphic(null);
                                    setStyle("-fx-background-color: rgba(12,16,30,0.98);");
                                } else {
                                    Product p = findProduct(productId);
                                    setText(p != null ? ("♥  " + p.getName()) : ("♥  #" + productId));
                                    setTextFill(Color.web("#E2E8F0"));
                                    setWrapText(true);
                                    setCursor(Cursor.HAND);
                                    setStyle(
                                            "-fx-background-color: rgba(255,255,255,0.06);"
                                                    + "-fx-background-radius: 8;"
                                                    + "-fx-padding: 6 8 6 8;"
                                    );
                                }
                            }
                        });
    }

    private void installDarkScrollBarStyle(Scene scene) {
        try {
            String css =
                    ".scroll-pane { -fx-background-color: transparent; }"
                            + ".scroll-pane > .viewport { -fx-background-color: transparent; }"
                            + ".scroll-bar:vertical, .scroll-bar:horizontal { -fx-background-color: rgba(0,0,0,0.4); }"
                            + ".scroll-bar .thumb { -fx-background-color: rgba(255,255,255,0.22);"
                            + "-fx-background-radius: 5; }"
                            + ".scroll-bar .thumb:pressed { -fx-background-color: rgba(255,255,255,0.35); }"
                            + ".list-view .placeholder .label { -fx-text-fill: #7283A7; }";
            scene.getStylesheets()
                    .add(
                            "data:text/css;charset=utf-8,"
                                    + URLEncoder.encode(css, StandardCharsets.UTF_8).replace("+", "%20"));
        } catch (Exception ignored) {
        }
    }

    private void addHoverZoom(Region node) {
        node.setOnMouseEntered(e -> animateScale(node, 1.02));
        node.setOnMouseExited(e -> animateScale(node, 1.0));
    }

    private void addButtonHover(Button btn, double scale) {
        btn.setOnMouseEntered(e -> animateScale(btn, scale));
        btn.setOnMouseExited(e -> animateScale(btn, 1.0));
    }

    private void animateScale(javafx.scene.Node node, double scale) {
        ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
        st.setToX(scale);
        st.setToY(scale);
        st.play();
    }

    private void playEntrance(javafx.scene.Node left, javafx.scene.Node center, javafx.scene.Node right) {
        animateFadeSlide(left, -16);
        animateFadeSlide(center, 10);
        animateFadeSlide(right, 16);
    }

    private void animateFadeSlide(javafx.scene.Node node, double fromX) {
        node.setOpacity(0);
        node.setTranslateX(fromX);
        FadeTransition fade = new FadeTransition(Duration.millis(400), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(400), node);
        slide.setFromX(fromX);
        slide.setToX(0);
        fade.play();
        slide.play();
    }

    public static void main(String[] args) {
        windowsFirewallWarmupIfNeeded();
        launch(args);
    }

    /**
     * On Windows, the first multicast bind often makes Defender show “allow on private networks” for
     * the JVM. We run this once per user profile (see preference) before JavaFX starts so the prompt
     * can appear before the UI. Override with {@code -Dchrionline.firewall.warmup.skip=true} or
     * {@code -Dchrionline.firewall.warmup.always=true}.
     */
    private static void windowsFirewallWarmupIfNeeded() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return;
        }
        if (Boolean.parseBoolean(System.getProperty("chrionline.firewall.warmup.skip", "false"))) {
            return;
        }
        boolean always = Boolean.parseBoolean(System.getProperty("chrionline.firewall.warmup.always", "false"));
        if (Boolean.parseBoolean(System.getProperty("chrionline.firewall.warmup.reset", "false"))) {
            ClientPrefs.remove(PREF_CLIENT, "firewallMulticastWarmupDone");
        }
        if (!always && ClientPrefs.getBoolean(PREF_CLIENT, "firewallMulticastWarmupDone", false)) {
            return;
        }
        try {
            LanDiscoveryClient.firewallWarmupForWindows();
        } catch (Throwable ignored) {
        } finally {
            if (!always) {
                ClientPrefs.putBoolean(PREF_CLIENT, "firewallMulticastWarmupDone", true);
            }
        }
    }
}
