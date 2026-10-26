/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.dialog;

import static net.sf.freecol.common.util.CollectionUtils.alwaysTrue;
import static net.sf.freecol.common.util.CollectionUtils.transform;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.control.TradeAppraisal;
import net.sf.freecol.client.gui.DialogHandler;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.panel.FreeColButton;
import net.sf.freecol.client.gui.panel.FreeColButton.ButtonStyle;
import net.sf.freecol.client.gui.panel.FreeColPanel;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.panel.Utility;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.diplomacy.SaleTerms;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTradeItem;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsLocation;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.InciteTradeItem;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitTradeItem;


/**
 * The panel that allows negotiations between players.
 * <p>
 * Panel Layout:
 * <p>
 * The Negotiation Dialog is laid out in three columns. The outer two columns are 30% of the panel's width while the
 *      center column is 40% the panel's width.
 * <p style="display: block; font-family: monospace; white-space: pre; margin: 1em 0;">
 * --------------------------------------------------------------------------
 * |                         negotiationDialog.title                        |
 * | ---------------------------------------------------------------------- |
 * |       Left Pane      |       Center Pane       |      Right Pane       |
 * |                      |      (centerPanel)      |                       |
 * | ---------------------------------------------------------------------- |
 * |                       [cancelButton][sendButton]                       |
 * --------------------------------------------------------------------------
 * <p>
 * The Left and Right Panes are laid out as follows:
 * <p style="display: block; font-family: monospace; white-space: pre; margin: 1em 0;">
 * | --------------- |
 * | gold            |
 * | --------------- |
 * | colony          |
 * | --------------- |
 * | stance          |
 * | --------------- |
 * | war declaration |
 * | --------------- |
 * TODO: Improve layout description
 */
public final class NegotiationDialog extends FreeColPanel {

    private static final Logger logger = Logger.getLogger(NegotiationDialog.class.getName());

    private static final int HUGE_DEMAND = 100000;

    /**
     * Width of the texts in the middle, in em.  Wider texts made the
     * dialog wider than its window, cutting off the right side.
     */
    private static final int SUMMARY_COLUMNS = 15;

    private static final String ADD = "add";
    private static final String CLEAR = "clear";

    /** The other player in the negotiation (!= getMyPlayer()). */
    private final Player otherPlayer;

    /** The agreement under negotiation. */
    private final DiplomaticTrade agreement;

    /** A comment message. */
    private final StringTemplate comment;

    /** The panels for various negotiable data. */
    private StanceTradeItemPanel stancePanel;
    private GoldTradeItemPanel goldOfferPanel, goldDemandPanel;
    private ColonyTradeItemPanel colonyOfferPanel, colonyDemandPanel;
    private GoodsTradeItemPanel goodsOfferPanel, goodsDemandPanel;
    private InciteTradeItemPanel inciteOfferPanel, inciteDemandPanel;
    private UnitTradeItemPanel unitOfferPanel, unitDemandPanel;
    /** What the other nation sells at its colony, if it said (mod). */
    private SaleGoodsPanel saleDemandPanel;
    private List<SaleTerms.Line> saleLines = null;
    private Colony otherColony = null;
    private Unit tradeUnit = null;

    /** A panel showing a summary of the current agreement. */
    private JPanel summary;

    /** What we know of the other player, for the price estimate. */
    private NationSummary otherSummary;

    /** Useful internal messages. */
    private StringTemplate demand, offer;
    private String exchangeMessage;

    /** Responses. */
    private FreeColButton send = null, accept = null;


    /**
     * Creates a new {@code NegotiationDialog} instance.
     *
     * @param freeColClient The {@code FreeColClient} for the game.
     * @param frame The owner frame.
     * @param our Our {@code FreeColGameObject} that is negotiating.
     * @param other The other {@code FreeColGameObject}.
     * @param agreement The {@code DiplomaticTrade} agreement that
     *     is being negotiated.
     * @param comment An optional {@code StringTemplate}
     *     commentary message.
     */
    public NegotiationDialog(FreeColClient freeColClient,
                             FreeColGameObject our, FreeColGameObject other,
                             DiplomaticTrade agreement, StringTemplate comment,
                             DialogHandler<DiplomaticTrade> handler) {
        super(freeColClient, null, new MigLayout("wrap 3, fill", "[][growprio 200][align right]", ""));

        final Player player = getMyPlayer();
        final Unit ourUnit = (our instanceof Unit) ? (Unit)our : null;
        final Colony ourColony = (our instanceof Colony) ? (Colony)our : null;

        this.otherPlayer = ((Ownable)other).getOwner();
        this.agreement = agreement;
        this.comment = comment;

        StringTemplate nation = player.getCountryLabel(),
                otherNation = otherPlayer.getCountryLabel();
        this.demand = StringTemplate.template("negotiationDialog.demand")
                .addStringTemplate("%nation%", nation)
                .addStringTemplate("%otherNation%", otherNation);
        this.offer = StringTemplate.template("negotiationDialog.offer")
                .addStringTemplate("%nation%", nation)
                .addStringTemplate("%otherNation%", otherNation);
        this.exchangeMessage = Messages.message("negotiationDialog.exchange");

        NationSummary ns = igc().nationSummary(otherPlayer);
        this.otherSummary = ns;
        int gold = (ns == null
                || ns.getGold() == Player.GOLD_NOT_ACCOUNTED) ? HUGE_DEMAND
                : ns.getGold();
        this.goldDemandPanel = new GoldTradeItemPanel(otherPlayer, gold);

        gold = (player.getGold() == Player.GOLD_NOT_ACCOUNTED) ? HUGE_DEMAND
                : player.getGold();
        this.goldOfferPanel = new GoldTradeItemPanel(player, gold);

        StringTemplate tutorial = null;
        TradeContext context = agreement.getContext();
        switch (context) {
            case CONTACT:
                if (freeColClient.tutorialMode()) {
                    tutorial = StringTemplate.key("negotiationDialog.contact.tutorial");
                }
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                break;
            case DIPLOMATIC:
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.colonyDemandPanel = new ColonyTradeItemPanel(otherPlayer);
                this.colonyOfferPanel = new ColonyTradeItemPanel(player);
                this.goodsDemandPanel = this.goodsOfferPanel = null;
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                this.unitOfferPanel = this.unitDemandPanel = null;
                break;
            case TRADE:
                this.stancePanel = null;
                this.colonyDemandPanel = this.colonyOfferPanel = null;
                List<Goods> goods = getAnyGoods();
                // A colony of a computer nation says what it sells and
                // for how much (mod); the placeholder goods of the old
                // dialog were never accepted.
                if (other instanceof Colony) {
                    this.otherColony = (Colony)other;
                    this.tradeUnit = ourUnit;
                    List<SaleTerms.Line> lines
                        = igc().getSaleTerms(this.otherColony);
                    if (!lines.isEmpty()) this.saleLines = lines;
                }
                if (this.saleLines != null) {
                    this.saleDemandPanel = new SaleGoodsPanel();
                    this.goodsDemandPanel = null;
                } else {
                    this.goodsDemandPanel = new GoodsTradeItemPanel(otherPlayer, goods);
                }
                GoodsLocation gl = (ourUnit != null) ? ourUnit : ourColony;
                goods = (ourUnit != null) ? ourUnit.getGoodsList()
                        : ourColony.getCompactGoodsList();
                for (Goods g : goods) {
                    if (g.getAmount() > GoodsContainer.CARGO_SIZE) {
                        g.setAmount(GoodsContainer.CARGO_SIZE);
                    }
                    g.setLocation(gl);
                }
                this.goodsOfferPanel = new GoodsTradeItemPanel(player, goods);
                this.inciteOfferPanel = this.inciteDemandPanel = null;
                this.unitDemandPanel = new UnitTradeItemPanel(otherPlayer,
                        getUnitUnitList(null));
                this.unitOfferPanel = new UnitTradeItemPanel(player,
                        ((ourUnit != null) ? getUnitUnitList(ourUnit)
                                : ourColony.getUnitList()));
                break;
            case TRIBUTE:
                this.stancePanel = new StanceTradeItemPanel(player, otherPlayer);
                this.colonyDemandPanel = this.colonyOfferPanel = null;
                this.goodsDemandPanel = this.goodsOfferPanel = null;
                this.inciteOfferPanel = new InciteTradeItemPanel(player, otherPlayer);
                this.inciteDemandPanel = new InciteTradeItemPanel(otherPlayer, player);
                this.unitOfferPanel = this.unitDemandPanel = null;
                break;
            default:
                throw new IllegalStateException("Bogus trade context: " + context);
        }

        this.summary = new MigPanel(new MigLayout("wrap 2", "[20px:n:n][]"));
        this.summary.setOpaque(false);
        this.summary.add(Utility.localizedTextArea(comment, SUMMARY_COLUMNS),
            "center, span 2");
        /**
         * Build Layout of Diplomatic Trade Dialog
         */

        // Main Panel Header
        add(Utility.localizedHeader("negotiationDialog.title."
                                          + agreement.getContext().getKey(),
                                          Utility.FONTSPEC_TITLE),
                "span 3, center");

        // Panel contents Header row
        //JLabel labelDemandMessage = new JLabel(Messages.message(this.demand));
        JTextArea labelDemandMessage = Utility.localizedTextArea(this.demand);
        Font font = FontLibrary.getScaledFont("normal-bold-tiny");
        labelDemandMessage.setFont(font);
        add(labelDemandMessage, "width 50:50:100%, grow");
        JTextArea blank = new JTextArea(" ");
        blank.setVisible(false);
        add(blank, "");
        
        JTextArea labelOfferMessage = Utility.localizedTextArea(this.offer);
        labelOfferMessage.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        labelOfferMessage.setFont(font);
        add(labelOfferMessage, "width 50:50:100%, grow");

        // Panel contents
        // TODO: Expand center panel so that contents fill cell horizontally. 
        add(this.goldDemandPanel, "sg item"); // Left pane
        JPanel centerPanel = new MigPanel(new MigLayout("wrap 1, fill"));
        centerPanel.setOpaque(false);
        //centerPanel.setMinimumSize(new Dimension(250, 50));
        if (tutorial != null) {
            // Display only if tutorial variable contents overriden
            //      Can only occur if: First Contact with a forgeign Nation
            JTextArea tutArea = Utility.localizedTextArea(tutorial, 30);
            centerPanel.add(tutArea, "top, wmin 200");
        }
        JScrollPane scroll = new JScrollPane(this.summary,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        centerPanel.add(scroll, "top, width 100%, wmin 200, grow");
        add(centerPanel, "spany, top, growx"); // Center pane
        add(this.goldOfferPanel, "sg item"); // Right pane

        if (this.colonyDemandPanel != null) {
            add(this.colonyDemandPanel, "sg item");
            add(this.colonyOfferPanel, "sg item");
        }
        if (this.stancePanel != null) {
            add(this.stancePanel, "skip, sg item");
        }
        if (this.saleDemandPanel != null) {
            add(this.saleDemandPanel, "sg item");
            add(this.goodsOfferPanel, "sg item");
        } else if (this.goodsDemandPanel != null) {
            add(this.goodsDemandPanel, "sg item");
            add(this.goodsOfferPanel, "sg item");
        }
        if (this.inciteDemandPanel != null) {
            add(this.inciteDemandPanel, "sg item");
            add(this.inciteOfferPanel, "sg item");
        }
        if (this.unitDemandPanel != null) {
            add(this.unitDemandPanel, "sg item");
            add(this.unitOfferPanel, "sg item");
        }
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            add(new JLabel("Version = " + agreement.getVersion()));
        }

        updateDialog(false);

        /*
        ImageIcon icon = new ImageIcon((otherColony != null)
                ? getImageLibrary().getScaledSettlementImage(otherColony)
                : getImageLibrary().getScaledUnitImage(otherUnit));
                */
        /*
        final JPanel empty = new JPanel();
        empty.setOpaque(false);
        add(empty, "newline, grow 200 200");
        */
        
        int numButtons = 0;
        if (agreement.getVersion() > 0) { // A new offer can not be accepted
            accept = new FreeColButton(Messages.message("negotiationDialog.accept")).withButtonStyle(ButtonStyle.IMPORTANT);
            accept.addActionListener(ae -> {
                getGUI().removeComponent(this);
                agreement.setStatus(TradeStatus.ACCEPT_TRADE);
                handler.handle(agreement);
            });
            okButton = accept;
            numButtons++;
        }
        
        send = new FreeColButton(Messages.message("negotiationDialog.send"));
        if (accept == null) {
            send.withButtonStyle(ButtonStyle.IMPORTANT);
            okButton = send;
        }
        send.addActionListener(ae -> {
            getGUI().removeComponent(this);
            agreement.setStatus(TradeStatus.PROPOSE_TRADE);
            handler.handle(agreement);
        });
        numButtons++;
        
        final FreeColButton cancel;
        if (agreement.getVersion() > 0 || context != TradeContext.CONTACT) {
            cancel = new FreeColButton(Messages.message("negotiationDialog.cancel"));
            cancel.addActionListener(ae -> {
                getGUI().removeComponent(this);
                agreement.setStatus(TradeStatus.REJECT_TRADE);
                handler.handle(agreement);
            });
            numButtons++;
        } else {
            cancel = null;
        }
        
        add(send, "newline, span 3, split " + numButtons + ((accept == null) ? ", tag ok " : ", tag next"));
        if (accept != null) {
            add(accept, "tag ok");
        }
        if (cancel != null) {
            add(cancel, "tag cancel");
            setEscapeAction(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    cancel.doClick();
                }
            });
        }
        
        setSize(getPreferredSize());
        // TOD: keybind ENTER + ESC
    }

    /**
     * Gets a list of all possible storable goods (one cargo load).
     * Note that these goods are fictional.  They are the goods that
     * *might* be in the other player's store.  Therefore they have a
     * bogus location (i.e. not the actual goods container).
     *
     * @return A list of storable {@code Goods}.
     */
    private List<Goods> getAnyGoods() {
        final Game game = getGame();
        final Specification spec = getSpecification();
        return transform(spec.getStorableGoodsTypeList(), alwaysTrue(),
                gt -> new Goods(game, null, gt, GoodsContainer.CARGO_SIZE));
    }

    /**
     * Get a list of units to offer that are associated with a given unit.
     *
     * @param unit The {@code Unit} that is trading.
     * @return A list of {@code Unit}s.
     */
    private List<Unit> getUnitUnitList(Unit unit) {
        List<Unit> ul = new ArrayList<>();
        if (unit != null) {
            if (unit.isCarrier()) {
                ul.addAll(unit.getUnitList());
            } else if (unit.isOnCarrier()) {
                ul.addAll(unit.getCarrier().getUnitList());
            } else {
                ul.add(unit);
            }
        }
        return ul;
    }

    /**
     * Update the entire dialog.
     *
     * @param changed If true, the agreement has changed, and the accept button
     *     needs to be disabled
     */
    private void updateDialog(boolean changed) {
        if (this.goldOfferPanel != null) {
            this.goldOfferPanel.update(agreement);
        }
        if (this.stancePanel != null) {
            this.stancePanel.update(agreement);
        }
        if (this.colonyOfferPanel != null) {
            this.colonyOfferPanel.update(agreement);
        }
        if (this.colonyDemandPanel != null) {
            this.colonyDemandPanel.update(agreement);
        }
        if (this.goodsOfferPanel != null) {
            this.goodsOfferPanel.update(agreement);
        }
        if (this.goodsDemandPanel != null) {
            this.goodsDemandPanel.update(agreement);
        }
        if (this.inciteOfferPanel != null) {
            this.inciteOfferPanel.update(agreement);
        }
        if (this.inciteDemandPanel != null) {
            this.inciteDemandPanel.update(agreement);
        }
        if (this.unitOfferPanel != null) {
            this.unitOfferPanel.update(agreement);
        }
        if (this.unitDemandPanel != null) {
            this.unitDemandPanel.update(agreement);
        }
        if (this.saleDemandPanel != null) {
            this.saleDemandPanel.update(agreement);
        }

        if (changed && this.accept != null) {
            this.accept.setEnabled(false);
        }
        updateSummary();
    }

    /**
     * Gets a trade item button for a given item.
     *
     * @param item The {@code TradeItem} to make a button for.
     * @param saleDir Boolean to indicate the EU price for sale (T) or buy (F)
     * @return A new {@code JButton} for the item.
     */
    private JButton getTradeItemButton(TradeItem item, boolean saleDir) {
        
        Market market = getMyPlayer().getMarket();
        JButton button = new JButton(new RemoveAction(item));
        
        // Checks if the items are goods
        if (item.getGoods() != null) {
            int buyPriceTotal = market.getBidPrice(item.getGoods().getType(), item.getGoods().getAmount());
            int salePriceTotal = market.getSalePrice(item.getGoods().getType(), item.getGoods().getAmount());
            
            // Depending on saleDir, creates a button for goods w/ EU buy or sale price
            if (saleDir) {
                button.setText(Messages.message(item.getLabel()) + " " +
                        Messages.message(StringTemplate
                                .template("negotiationDialog.euSalePrice")
                                .addAmount("%priceTotal%", salePriceTotal)));
            } else {
                button.setText(Messages.message(item.getLabel()) + " " +
                        Messages.message(StringTemplate
                                .template("negotiationDialog.euBuyPrice")
                                .addAmount("%priceTotal%", buyPriceTotal)));
            }
        } else {
            // If not goods, follow protocol
            button.setText(Messages.message(item.getLabel()));
        }
        
        button.setMargin(Utility.EMPTY_MARGIN);
        button.setOpaque(false);
        button.setForeground(Utility.getLinkColor());
        button.setBorder(Utility.blankBorder(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * Update the text summary of the proposed transaction.
     */
    private void updateSummary() {
        final Player player = getMyPlayer();

        summary.removeAll();

        summary.add(Utility.localizedTextArea(comment, SUMMARY_COLUMNS),
            "center, span 2");

        List<TradeItem> offers = agreement.getItemsGivenBy(player);
        if (!offers.isEmpty()) {
            summary.add(Utility.localizedLabel(this.offer), "span");
            for (TradeItem item : offers) {
                summary.add(getTradeItemButton(item, true), "skip");
            }
        }

        List<TradeItem> demands = agreement.getItemsGivenBy(otherPlayer);
        if (!demands.isEmpty()) {
            if (offers.isEmpty()) {
                summary.add(Utility.localizedLabel(this.demand), "span");
            } else {
                summary.add(new JLabel(exchangeMessage), "newline 20, span");
            }
            for (TradeItem item : demands) {
                summary.add(getTradeItemButton(item, false), "skip");
            }
        }

        // How does the deal look to a computer player?
        if (agreement.getContext() == TradeContext.TRADE
            && otherPlayer.isAI() && otherPlayer.isEuropean()) {
            final NationSummary ns = this.otherSummary;
            TradeAppraisal ta = TradeAppraisal.appraise(agreement, player,
                otherPlayer, (ns == null) ? -1 : ns.getTax(),
                (ns == null) ? -1 : ns.getNumberOfUnits(),
                (ns == null || ns.getGold() == Player.GOLD_NOT_ACCOUNTED)
                    ? -1 : ns.getGold());
            StringTemplate t = appraisalText(ta);
            if (t != null) {
                JTextArea text = Utility.localizedTextArea(t, SUMMARY_COLUMNS);
                text.setFont(FontLibrary.getScaledFont("normal-bold-tiny"));
                summary.add(text, "newline 20, span, growx");
                summary.add(Utility.localizedTextArea(StringTemplate
                        .key("negotiationDialog.appraisal.note"),
                        SUMMARY_COLUMNS),
                    "span, growx");
            }
        }
    }

    /**
     * The text for a price estimate.
     *
     * @param ta The {@code TradeAppraisal}.
     * @return A {@code StringTemplate}, or null if there is nothing
     *     to say.
     */
    private StringTemplate appraisalText(TradeAppraisal ta) {
        final String prefix = "negotiationDialog.appraisal.";
        final StringTemplate nation = otherPlayer.getCountryLabel();
        switch (ta.verdict) {
        case ASK:
            return StringTemplate.template(prefix + "ask")
                .addStringTemplate("%nation%", nation)
                .addAmount("%value%", (ta.theirGold >= 0)
                    ? Math.min(ta.value, ta.theirGold) : ta.value);
        case LOW_BALL:
            return StringTemplate.template(prefix + "lowBall")
                .addStringTemplate("%nation%", nation)
                .addAmount("%ask%", ta.ask)
                .addAmount("%value%", (ta.theirGold >= 0)
                    ? Math.min(ta.value, ta.theirGold) : ta.value);
        case FAIR:
            return StringTemplate.template(prefix + "fair")
                .addStringTemplate("%nation%", nation)
                .addAmount("%ask%", ta.ask)
                .addAmount("%value%", ta.value);
        case HIGH_BALL:
            return StringTemplate.template(prefix + ((ta.counter < 0)
                    ? "highBallBroke" : (ta.counter == 0)
                    ? "highBallDrop" : "highBall"))
                .addStringTemplate("%nation%", nation)
                .addAmount("%ask%", ta.ask)
                .addAmount("%value%", ta.value)
                .addAmount("%counter%", ta.counter)
                .addAmount("%gold%", ta.theirGold);
        case SHORT:
            return StringTemplate.template(prefix + "short")
                .addStringTemplate("%nation%", nation)
                .addAmount("%short%", -ta.value);
        default:
            return null;
        }
    }


    /**
     * Remove trade items of a given type.
     *
     * @param itemClass The class of {@code TradeItem} to remove.
     */
    public void removeTradeItems(Class<? extends TradeItem> itemClass) {
        this.agreement.removeType(itemClass);
        updateDialog(true);
    }

    /**
     * Adds a {@code ColonyTradeItem} to the list of TradeItems.
     *
     * @param source The sourced {@code Player}.
     * @param colony The {@code Colony} to add.
     */
    public void addColonyTradeItem(Player source, Colony colony) {
        final Player player = getMyPlayer();

        Player destination = (source == otherPlayer) ? player : otherPlayer;
        agreement.add(new ColonyTradeItem(getGame(), source, destination, colony));
        updateDialog(true);
    }

    /**
     * Adds a {@code GoldTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param amount The amount of gold.
     */
    public void addGoldTradeItem(Player source, int amount) {
        final Player player = getMyPlayer();

        Player destination = (source == otherPlayer) ? player : otherPlayer;
        agreement.add(new GoldTradeItem(getGame(), source, destination, amount));
        updateDialog(true);
    }

    /**
     * Adds a {@code GoodsTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param goods The {@code Goods} to add.
     */
    public void addGoodsTradeItem(Player source, Goods goods) {
        final Player player = getMyPlayer();

        Player destination = (source == otherPlayer) ? player : otherPlayer;
        agreement.add(new GoodsTradeItem(getGame(), source, destination, goods));
        updateDialog(true);
    }

    /**
     * Add an {@code InciteTradeItem} to the list of trade items.
     *
     * @param source The source {@code Player}.
     * @param victim The {@code Player} to be attacked.
     */
    public void addInciteTradeItem(Player source, Player victim) {
        final Player player = getMyPlayer();

        Player destination = (source == otherPlayer) ? player : otherPlayer;
        agreement.add(new InciteTradeItem(getGame(), source, destination, victim));
        updateDialog(true);
    }

    /**
     * Trade a stance change between the players.
     *
     * @param stance The {@code Stance} to trade.
     */
    public void addStanceTradeItem(Stance stance) {
        final Player player = getMyPlayer();

        agreement.add(new StanceTradeItem(getGame(), otherPlayer, player, stance));
        updateDialog(true);
    }

    /**
     * Adds a {@code UnitTradeItem} to the list of TradeItems.
     *
     * @param source The source {@code Player}.
     * @param unit The {@code Unit} to add.
     */
    public void addUnitTradeItem(Player source, Unit unit) {
        final Player player = getMyPlayer();

        Player destination = (source == otherPlayer) ? player : otherPlayer;
        agreement.add(new UnitTradeItem(getGame(), source, destination, unit));
        updateDialog(true);
    }


    // Override Component

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNotify() {
        super.addNotify();
        // The wrapped texts only know their height once laid out, and
        // the window was sized before that, cutting off the right
        // side.  Size the window again once everything is in place.
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JInternalFrame f = (javax.swing.JInternalFrame)
                javax.swing.SwingUtilities.getAncestorOfClass(
                    javax.swing.JInternalFrame.class, this);
            if (f == null) return;
            f.pack();
            java.awt.Container p = f.getParent();
            if (p != null) {
                // Keep it on the screen.
                int x = Math.max(0, Math.min(f.getX(),
                        p.getWidth() - f.getWidth()));
                int y = Math.max(0, Math.min(f.getY(),
                        p.getHeight() - f.getHeight()));
                f.setLocation(x, y);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        super.removeNotify();

        removeAll();

        this.stancePanel = null;
        this.goldOfferPanel = this.goldDemandPanel = null;
        this.colonyOfferPanel = this.colonyDemandPanel = null;
        this.goodsOfferPanel = this.goodsDemandPanel = null;
        this.inciteOfferPanel = this.inciteDemandPanel = null;
        this.unitOfferPanel = this.unitDemandPanel = null;
        this.summary = null;
        this.demand = this.offer = null;
        this.exchangeMessage = null;
    }
    
    
    private class RemoveAction extends AbstractAction {
        private final TradeItem item;

        public RemoveAction(TradeItem item) {
            this.item = item;
        }

        // Interface ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            agreement.remove(item);
            // Goods bought on their terms: their price comes off our
            // gold too (mod).
            final SaleTerms.Line l = saleLineFor(item);
            if (l != null) {
                final Player player = getMyPlayer();
                final int gold = agreement.getGoldGivenBy(player);
                if (gold >= 0) {
                    agreement.removeType(GoldTradeItem.class);
                    final int left = gold - l.priceFor(item.getGoods().getAmount());
                    if (left > 0) {
                        agreement.add(new GoldTradeItem(getGame(), player,
                                                        otherPlayer, left));
                    }
                }
            }
            updateDialog(true);
        }
    }

    /**
     * The sale terms line for goods the other nation gives (mod).
     *
     * @param item A {@code TradeItem}.
     * @return Its {@code SaleTerms.Line}, or null.
     */
    private SaleTerms.Line saleLineFor(TradeItem item) {
        if (this.saleLines == null || !(item instanceof GoodsTradeItem)
            || item.getSource() != otherPlayer
            || item.getGoods() == null) return null;
        return SaleTerms.find(this.saleLines,
                              item.getGoods().getType().getId());
    }

    /**
     * Buy goods from the other nation on its terms (mod): the goods from
     * its colony, and their price added to our gold.
     *
     * @param line The {@code SaleTerms.Line}.
     * @param amount How many.
     */
    private void buyOnTerms(SaleTerms.Line line, int amount) {
        final Player player = getMyPlayer();
        final GoodsType type = getSpecification().getGoodsType(line.goodsId);
        if (type == null || amount <= 0) return;
        // The same goods again: one item with the total.
        int had = 0;
        for (TradeItem ti : new ArrayList<>(agreement.getItemsGivenBy(otherPlayer))) {
            if (ti instanceof GoodsTradeItem && ti.getGoods() != null
                && ti.getGoods().getType() == type) {
                had += ti.getGoods().getAmount();
                agreement.remove(ti);
            }
        }
        final int total = Math.min(line.max, had + amount);
        final int extra = line.priceFor(total) - line.priceFor(had);
        // We can not see their warehouse, so the goods are made without
        // a place and then put in their colony (the constructor refuses
        // a colony whose store we do not know).
        final Goods goods = new Goods(getGame(), null, type, total);
        goods.setLocation(this.otherColony);
        agreement.add(new GoodsTradeItem(getGame(), otherPlayer, player,
                                         goods));
        final int gold = Math.max(0, agreement.getGoldGivenBy(player));
        agreement.add(new GoldTradeItem(getGame(), player, otherPlayer,
                                        gold + extra));
        updateDialog(true);
    }

    /**
     * Haggle over the goods (mod): they agree to a discount or refuse.
     * Goods already on the table get the new price.
     *
     * @param line The {@code SaleTerms.Line}.
     * @param off Percent more off asked.
     * @return What came of it ("AGREED", "REFUSED", "CLOSED"), or null.
     */
    private String haggleOver(SaleTerms.Line line, int off) {
        final String result = igc().haggle(this.otherColony, line.goodsId,
                                           off);
        final List<SaleTerms.Line> lines = igc().getSaleTerms(this.otherColony);
        if (lines.isEmpty()) return result;
        final SaleTerms.Line now = SaleTerms.find(lines, line.goodsId);
        // Goods of this kind on the table: their price changes.
        if (now != null) {
            final GoodsType type = getSpecification().getGoodsType(line.goodsId);
            int had = 0;
            for (TradeItem ti : agreement.getItemsGivenBy(otherPlayer)) {
                if (ti instanceof GoodsTradeItem && ti.getGoods() != null
                    && ti.getGoods().getType() == type) {
                    had += ti.getGoods().getAmount();
                }
            }
            final Player player = getMyPlayer();
            final int gold = agreement.getGoldGivenBy(player);
            final int less = line.priceFor(had) - now.priceFor(had);
            if (had > 0 && gold >= 0 && less != 0) {
                agreement.add(new GoldTradeItem(getGame(), player,
                    otherPlayer, Math.max(0, gold - less)));
            }
        }
        this.saleLines = lines;
        updateDialog(true);
        return result;
    }

    /**
     * What the other nation sells at its colony (mod): pick the goods,
     * see what they ask, choose the amount (the total shows as you
     * type) and haggle. Goods they have none of, or will not sell, are
     * greyed out.
     */
    private class SaleGoodsPanel extends MigPanel {

        private final JComboBox<SaleTerms.Line> box;
        private final JLabel ask;
        private final JSpinner spinner;
        private final JLabel price;
        private final JButton addButton;
        private final JComboBox<Integer> haggleBox;
        private final JButton haggleButton;
        private final JLabel haggleText;
        private boolean filling = false;

        public SaleGoodsPanel() {
            super(new MigLayout("wrap 1", "", ""));
            this.box = new JComboBox<>();
            this.box.setRenderer(new ListCellRenderer<SaleTerms.Line>() {
                private final JLabel label = new JLabel();
                @Override
                public Component getListCellRendererComponent(
                    JList<? extends SaleTerms.Line> list, SaleTerms.Line l,
                    int index, boolean isSelected, boolean hasFocus) {
                    if (l == null) {
                        label.setText("");
                        label.setIcon(null);
                        return label;
                    }
                    final GoodsType gt = getSpecification()
                        .getGoodsType(l.goodsId);
                    label.setIcon(new javax.swing.ImageIcon(
                        getImageLibrary().getSmallerGoodsTypeImage(gt)));
                    label.setText(Messages.message(StringTemplate
                        .template("negotiationDialog.sale."
                            + l.status.name().toLowerCase(java.util.Locale.ROOT))
                        .addNamed("%goods%", gt)
                        .addAmount("%amount%", l.max)));
                    label.setEnabled(l.status == SaleTerms.Status.FOR_SALE);
                    label.setOpaque(isSelected);
                    return label;
                }
            });
            this.ask = new JLabel();
            this.spinner = new JSpinner(new SpinnerNumberModel(0, 0, 0,
                                                               SaleTerms.STEP));
            final javax.swing.JFormattedTextField field
                = ((JSpinner.DefaultEditor)this.spinner.getEditor())
                    .getTextField();
            field.setColumns(4);
            // The total follows every key, not only Enter.
            if (field.getFormatter() instanceof javax.swing.text.DefaultFormatter) {
                ((javax.swing.text.DefaultFormatter)field.getFormatter())
                    .setCommitsOnValidEdit(true);
            }
            field.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { showPrice(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { showPrice(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { showPrice(); }
                });
            this.price = new JLabel();
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(ae -> {
                final SaleTerms.Line l = (SaleTerms.Line)box.getSelectedItem();
                final int n = amount();
                if (l != null && n > 0) buyOnTerms(l, n);
            });
            this.haggleBox = new JComboBox<>();
            for (int pct : SaleTerms.HAGGLE_STEPS) this.haggleBox.addItem(pct);
            this.haggleBox.setRenderer(new ListCellRenderer<Integer>() {
                private final JLabel label = new JLabel();
                @Override
                public Component getListCellRendererComponent(
                    JList<? extends Integer> list, Integer v, int index,
                    boolean isSelected, boolean hasFocus) {
                    label.setText((v == null) ? "" : Messages.message(
                        StringTemplate.template("negotiationDialog.sale.off")
                            .addAmount("%percent%", v)));
                    label.setOpaque(isSelected);
                    return label;
                }
            });
            this.haggleButton = Utility.localizedButton("negotiationDialog.sale.haggleAsk");
            this.haggleText = new JLabel();
            this.haggleButton.addActionListener(ae -> {
                final SaleTerms.Line l = (SaleTerms.Line)box.getSelectedItem();
                final Integer pct = (Integer)haggleBox.getSelectedItem();
                if (l == null || pct == null) return;
                final String r = haggleOver(l, pct);
                final String key = (r == null) ? "closed"
                    : r.toLowerCase(java.util.Locale.ROOT);
                haggleText.setText(Messages.message(StringTemplate
                    .template("negotiationDialog.sale." + key)
                    .addAmount("%percent%", pct)));
            });
            this.box.addActionListener(ae -> {
                if (!filling) {
                    haggleText.setText("");
                    select();
                }
            });

            setBorder(Utility.getSimpleLineBorder());
            add(Utility.localizedLabel("negotiationDialog.sale.title"));
            add(this.box);
            add(this.ask);
            add(Utility.localizedLabel("negotiationDialog.sale.amount"), "split 3");
            add(this.spinner);
            add(this.price);
            add(this.addButton);
            add(Utility.localizedLabel("negotiationDialog.sale.haggle"), "split 3");
            add(this.haggleBox);
            add(this.haggleButton);
            add(this.haggleText);
            fill();
            // Start on the first goods they sell.
            for (int i = 0; i < box.getItemCount(); i++) {
                if (box.getItemAt(i).status == SaleTerms.Status.FOR_SALE) {
                    box.setSelectedIndex(i);
                    break;
                }
            }
            select();
            setSize(getPreferredSize());
        }

        /** Put the lines in the box, keeping the selected goods. */
        private void fill() {
            final SaleTerms.Line was = (SaleTerms.Line)box.getSelectedItem();
            filling = true;
            box.removeAllItems();
            int sel = -1;
            for (SaleTerms.Line l : saleLines) {
                if (getSpecification().getGoodsType(l.goodsId) == null) continue;
                if (was != null && was.goodsId.equals(l.goodsId)) {
                    sel = box.getItemCount();
                }
                box.addItem(l);
            }
            if (sel >= 0) box.setSelectedIndex(sel);
            filling = false;
        }

        /** The amount typed, even before Enter. */
        private int amount() {
            final String t = ((JSpinner.DefaultEditor)spinner.getEditor())
                .getTextField().getText().replaceAll("[^0-9]", "");
            final int max = ((Integer)((SpinnerNumberModel)spinner.getModel())
                             .getMaximum());
            try {
                return Math.max(0, Math.min(max, Integer.parseInt(t)));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** Most of the selected goods we can still take. */
        private int room(SaleTerms.Line l) {
            int max = l.max;
            final GoodsType type = getSpecification().getGoodsType(l.goodsId);
            for (TradeItem ti : agreement.getItemsGivenBy(otherPlayer)) {
                if (ti instanceof GoodsTradeItem && ti.getGoods() != null
                    && ti.getGoods().getType() == type) {
                    max -= ti.getGoods().getAmount();
                }
            }
            // No more than the ship or wagon can carry.
            if (tradeUnit != null && type != null) {
                max = Math.min(max, tradeUnit.getLoadableAmount(type));
            }
            return Math.max(0, max);
        }

        private void select() {
            final SaleTerms.Line l = (SaleTerms.Line)box.getSelectedItem();
            final boolean sells = l != null
                && l.status == SaleTerms.Status.FOR_SALE;
            final int max = (sells) ? room(l) : 0;
            final SpinnerNumberModel m = (SpinnerNumberModel)spinner.getModel();
            m.setMinimum(0);
            m.setMaximum(max);
            m.setValue(Math.min(max, SaleTerms.STEP));
            spinner.setEnabled(max > 0);
            addButton.setEnabled(max > 0);
            haggleBox.setEnabled(sells && !l.closed);
            haggleButton.setEnabled(sells && !l.closed);
            ask.setText((!sells) ? "" : Messages.message(StringTemplate
                .template((l.off > 0) ? "negotiationDialog.sale.askOff"
                          : "negotiationDialog.sale.ask")
                .addAmount("%gold%", l.priceFor(SaleTerms.STEP))
                .addAmount("%amount%", SaleTerms.STEP)
                .addAmount("%percent%", l.off)));
            showPrice();
        }

        private void showPrice() {
            final SaleTerms.Line l = (SaleTerms.Line)box.getSelectedItem();
            final int n = amount();
            price.setText((l == null || n <= 0
                           || l.status != SaleTerms.Status.FOR_SALE) ? ""
                : Messages.message(StringTemplate
                    .template("negotiationDialog.sale.price")
                    .addAmount("%gold%", l.priceFor(n))));
        }

        public void update(DiplomaticTrade dt) {
            fill();
            select();
        }
    }

    private class ColonyTradeItemPanel extends MigPanel
            implements ActionListener {

        private final Player source;
        private final JComboBox<Colony> colonyBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Colony> allColonies;


        /**
         * Creates a new {@code ColonyTradeItemPanel} instance.
         *
         * @param source The {@code Player} source.
         */
        public ColonyTradeItemPanel(Player source) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.colonyBox = new JComboBox<>();
            this.clearButton = Utility.localizedButton("negotiationDialog.clear");
            this.clearButton.addActionListener(this);
            this.clearButton.setActionCommand(CLEAR);
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(this);
            this.addButton.setActionCommand(ADD);
            this.label = Utility.localizedLabel(Messages.getName("model.tradeItem.colony"));
            this.allColonies = source.getColonyList();

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.colonyBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        private void update(DiplomaticTrade dt) {
            if (!source.isEuropean()) return;

            // Remove all action listeners, so the update has no effect (except
            // updating the list).
            ActionListener[] listeners = this.colonyBox.getActionListeners();
            for (ActionListener al : listeners) {
                this.colonyBox.removeActionListener(al);
            }

            List<Colony> available = new ArrayList<>(allColonies);
            for (Colony c : dt.getColoniesGivenBy(source)) {
                if (available.contains(c)) {
                    available.remove(c);
                } else {
                    allColonies.add(c); // did not know about this!
                }
            }

            this.colonyBox.removeAllItems();
            for (Colony c : available) this.colonyBox.addItem(c);

            boolean enable = !available.isEmpty();
            this.clearButton.setEnabled(!enable);
            this.addButton.setEnabled(enable);
            this.colonyBox.setEnabled(enable);
            this.label.setEnabled(enable);

            for (ActionListener al : listeners) {
                this.colonyBox.addActionListener(al);
            }
        }


        // Implement ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    NegotiationDialog.this.addColonyTradeItem(source,
                            (Colony)colonyBox.getSelectedItem());
                    break;
                case CLEAR:
                    NegotiationDialog.this
                            .removeTradeItems(ColonyTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    private class GoldTradeItemPanel extends MigPanel
            implements ActionListener {

        private final Player source;
        private final JSpinner spinner;


        /**
         * Creates a new {@code GoldTradeItemPanel} instance.
         *
         * @param source The {@code Player} that is trading.
         * @param gold The maximum amount of gold to trade.
         */
        public GoldTradeItemPanel(Player source, int gold) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.spinner = new JSpinner(new SpinnerNumberModel(0, 0, gold, 1));
            JButton clearButton = Utility.localizedButton("negotiationDialog.clear");
            clearButton.addActionListener(this);
            clearButton.setActionCommand(CLEAR);
            JButton addButton = Utility.localizedButton("negotiationDialog.add");
            addButton.addActionListener(this);
            addButton.setActionCommand(ADD);
            // adjust entry size
            ((JSpinner.DefaultEditor)this.spinner.getEditor())
                                                 .getTextField()
                                                 .setColumns(5);

            setBorder(Utility.getSimpleLineBorder());

            add(Utility.localizedLabel(Messages.getName("model.tradeItem.gold")));
            add(Utility.localizedLabel(StringTemplate
                    .template("negotiationDialog.goldAvailable")
                    .addAmount("%amount%", gold)));
            add(this.spinner);
            add(clearButton, "split 2");
            add(addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        public void update(DiplomaticTrade dt) {
            int gold = dt.getGoldGivenBy(source);
            if (gold >= 0) {
                SpinnerNumberModel model
                        = (SpinnerNumberModel)spinner.getModel();
                model.setValue(gold);
            }
        }


        // Implement ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    int amount = ((Integer)spinner.getValue());
                    NegotiationDialog.this.addGoldTradeItem(source, amount);
                    break;
                case CLEAR:
                    NegotiationDialog.this.removeTradeItems(GoldTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    private class GoodsTradeItemPanel extends MigPanel
        implements ActionListener {

        private class GoodsBoxRenderer extends JLabel
                implements ListCellRenderer<Goods> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Goods> list,
                                                          Goods value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? ""
                        : Messages.message(value.getLabel(true)));
                return this;
            }
        }

        private final Player source;
        private final JComboBox<Goods> goodsBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Goods> allGoods;


        /**
         * Creates a new {@code GoodsTradeItemPanel} instance.
         *
         * @param source The {@code Player} nominally in possession of the
         *     goods (this may be totally fictional).
         * @param allGoods The {@code Goods} to trade.
         */
        public GoodsTradeItemPanel(Player source, List<Goods> allGoods) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.goodsBox = new JComboBox<>(new DefaultComboBoxModel<Goods>());
            this.goodsBox.setRenderer(new GoodsBoxRenderer());
            this.clearButton = Utility.localizedButton("negotiationDialog.clear");
            this.clearButton.addActionListener(this);
            this.clearButton.setActionCommand(CLEAR);
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(this);
            this.addButton.setActionCommand(ADD);
            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.goods"));
            this.allGoods = allGoods;

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.goodsBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        public void update(DiplomaticTrade dt) {
            // Remove all action listeners, so the update has no
            // effect (except updating the list).
            ActionListener[] listeners = this.goodsBox.getActionListeners();
            for (ActionListener al : listeners) {
                this.goodsBox.removeActionListener(al);
            }

            List<Goods> available = new ArrayList<>(allGoods);
            for (Goods goods : dt.getGoodsGivenBy(source)) {
                // Remove the ones already on the table
                for (int i = 0; i < available.size(); i++) {
                    Goods g = available.get(i);
                    if (g.getType() == goods.getType()) {
                        if (g.getAmount() <= goods.getAmount()) {
                            available.remove(i);
                        } else {
                            g.setAmount(g.getAmount() - goods.getAmount());
                        }
                        break;
                    }
                }
            }

            this.goodsBox.removeAllItems();
            for (Goods g : available) goodsBox.addItem(g);

            boolean enable = !available.isEmpty();
            this.label.setEnabled(enable);
            this.clearButton.setEnabled(!enable);
            this.addButton.setEnabled(enable);
            this.goodsBox.setEnabled(enable);

            for (ActionListener al : listeners) {
                this.goodsBox.addActionListener(al);
            }
        }


        // Interface ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    NegotiationDialog.this.addGoodsTradeItem(source,
                            (Goods)goodsBox.getSelectedItem());
                    break;
                case CLEAR:
                    NegotiationDialog.this
                            .removeTradeItems(GoodsTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    private class InciteTradeItemPanel extends MigPanel
        implements ActionListener {

        private class InciteBoxRenderer extends JLabel
                implements ListCellRenderer<Player> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Player> list,
                                                          Player value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? ""
                        : Messages.message(value.getNationLabel()));
                return this;
            }
        }

        private final Player source;
        private final Player other;
        private final JComboBox<Player> victimBox;
        private final JLabel label;
        private final JButton clearButton;
        private final JButton addButton;
        private final List<Player> available = new ArrayList<>();


        /**
         * Creates a new {@code InciteTradeItemPanel} instance.
         *
         * @param source The {@code Player} that is trading.
         * @param other The {@code Player} negotiated with.
         */
        public InciteTradeItemPanel(Player source, Player other) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.other = other;
            this.victimBox = new JComboBox<>(new DefaultComboBoxModel<Player>());
            this.victimBox.setRenderer(new InciteBoxRenderer());
            this.clearButton = Utility.localizedButton("negotiationDialog.clear");
            this.clearButton.addActionListener(this);
            this.clearButton.setActionCommand(CLEAR);
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(this);
            this.addButton.setActionCommand(ADD);
            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.incite"));

            setBorder(Utility.getSimpleLineBorder());

            available.clear();
            final Predicate<Player> incitablePred = p ->
                    p != this.other && this.source.getStance(p).isIncitable();
            available.addAll(transform(getGame().getLivePlayers(this.source),
                    incitablePred));

            add(this.label);
            add(this.victimBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        public void update(DiplomaticTrade dt) {
            // Remove all action listeners, so the update has no
            // effect (except updating the list).
            ActionListener[] listeners = this.victimBox.getActionListeners();
            for (ActionListener al : listeners) {
                this.victimBox.removeActionListener(al);
            }

            this.victimBox.removeAllItems();
            for (Player p : available) victimBox.addItem(p);

            boolean enable = !available.isEmpty();
            this.label.setEnabled(enable);
            this.clearButton.setEnabled(!enable);
            this.addButton.setEnabled(enable);
            this.victimBox.setEnabled(enable);

            for (ActionListener al : listeners) {
                this.victimBox.addActionListener(al);
            }
        }


        // Implement ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    Player victim = (Player)victimBox.getSelectedItem();
                    if (victim != null) {
                        NegotiationDialog.this
                                .addInciteTradeItem(source, victim);
                    }   break;
                case CLEAR:
                    NegotiationDialog.this
                            .removeTradeItems(InciteTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    /**
     * Class for the stance trade panel.  Access needs to be public so
     * that comboBoxLabel() is externally visible.
     */
    public class StanceTradeItemPanel extends MigPanel
        implements ActionListener {

        private class StanceBoxRenderer extends JLabel
                implements ListCellRenderer<Stance> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Stance> list,
                                                          Stance value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? "" : Messages.getName(value));
                return this;
            }
        }

        private final Player source;
        private final Player target;
        private final JComboBox<Stance> stanceBox;
        private final JButton clearButton;
        private final JButton addButton;


        /**
         * Creates a new {@code StanceTradeItemPanel} instance.
         *
         * @param source The {@code Player} offering the stance change.
         * @param target The {@code Player} to consider the stance change.
         */
        public StanceTradeItemPanel(Player source, Player target) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.target = target;
            this.stanceBox = new JComboBox<>(new DefaultComboBoxModel<Stance>());
            this.stanceBox.setRenderer(new StanceBoxRenderer());
            this.clearButton = Utility.localizedButton("negotiationDialog.clear");
            this.clearButton.addActionListener(this);
            this.clearButton.setActionCommand(CLEAR);
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(this);
            this.addButton.setActionCommand(ADD);

            setBorder(Utility.getSimpleLineBorder());

            add(Utility.localizedLabel(Messages.nameKey("model.tradeItem.stance")));
            add(this.stanceBox);
            add(this.clearButton, "split 2");
            add(this.addButton);
        }


        /**
         * Select the item with a given stance.
         *
         * @param stance The {@code Stance} to select.
         */
        private void setSelectedValue(Stance stance) {
            for (int i = 0; i < stanceBox.getItemCount(); i++) {
                if (stanceBox.getItemAt(i) == stance) {
                    stanceBox.setSelectedItem(i);
                }
            }
        }

        /**
         * Update this panel with a given trade.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        public void update(DiplomaticTrade dt) {
            stanceBox.removeAllItems();

            Stance stance = source.getStance(target);
            if (stance != Stance.WAR) {
                stanceBox.addItem(Stance.WAR);
            } else {
                stanceBox.addItem(Stance.CEASE_FIRE);
            }
            if (stance != Stance.PEACE) {
                stanceBox.addItem(Stance.PEACE);
            }
            if (stance == Stance.PEACE) {
                stanceBox.addItem(Stance.ALLIANCE);
            }

            Stance select = dt.getStance();
            if (select != null) setSelectedValue(select);
        }


        // Interface ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    Stance stance = (Stance)stanceBox.getSelectedItem();
                    NegotiationDialog.this.addStanceTradeItem(stance);
                    break;
                case CLEAR:
                    NegotiationDialog.this
                            .removeTradeItems(StanceTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }

    private class UnitTradeItemPanel extends MigPanel
        implements ActionListener {

        private class UnitBoxRenderer extends JLabel
                implements ListCellRenderer<Unit> {

            /**
             * {@inheritDoc}
             */
            @Override
            public Component getListCellRendererComponent(JList<? extends Unit> list,
                                                          Unit value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                setText((value == null) ? "" : value.getDescription());
                return this;
            }
        }

        private final Player source;
        private final JComboBox<Unit> unitBox;
        private final JButton clearButton;
        private final JButton addButton;
        private final JLabel label;
        private final List<Unit> allUnits;


        /**
         * Creates a new {@code UnitTradeItemPanel} instance.
         *
         * @param source The {@code Player} nominally in posession of the
         *     unit (this may be totally fictional).
         * @param allUnits The {@code Unit}s to trade.
         */
        public UnitTradeItemPanel(Player source, List<Unit> allUnits) {
            super(new MigLayout("wrap 1", "", ""));

            this.source = source;
            this.unitBox = new JComboBox<>(new DefaultComboBoxModel<Unit>());
            this.unitBox.setRenderer(new UnitBoxRenderer());
            this.clearButton = Utility.localizedButton("negotiationDialog.clear");
            this.clearButton.addActionListener(this);
            this.clearButton.setActionCommand(CLEAR);
            this.addButton = Utility.localizedButton("negotiationDialog.add");
            this.addButton.addActionListener(this);
            this.addButton.setActionCommand(ADD);
            this.label = Utility.localizedLabel(Messages.nameKey("model.tradeItem.unit"));
            this.allUnits = allUnits;

            setBorder(Utility.getSimpleLineBorder());

            add(this.label);
            add(this.unitBox);
            add(this.clearButton, "split 2");
            add(this.addButton);

            setSize(getPreferredSize());
        }


        /**
         * Update this panel with a given trade.
         *
         * @param dt The {@code DiplomaticTrade} to update with.
         */
        private void update(DiplomaticTrade dt) {
            // Remove all action listeners, so the update has no
            // effect (except updating the list).
            ActionListener[] listeners = unitBox.getActionListeners();
            for (ActionListener al : listeners) {
                unitBox.removeActionListener(al);
            }

            List<Unit> available = new ArrayList<>(allUnits);
            for (Unit u : dt.getUnitsGivenBy(source)) {
                // Remove the ones already on the table
                if (available.contains(u)) {
                    available.remove(u);
                } else {
                    allUnits.add(u); // Did not know about this!
                }
            }

            unitBox.removeAllItems();
            for (Unit u : available) unitBox.addItem(u);

            boolean enable = !available.isEmpty();
            this.label.setEnabled(enable);
            clearButton.setEnabled(!enable);
            addButton.setEnabled(enable);
            unitBox.setEnabled(enable);

            for (ActionListener al : listeners) {
                unitBox.addActionListener(al);
            }
        }


        // Interface ActionListener

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionPerformed(ActionEvent ae) {
            final String command = ae.getActionCommand();
            if (null != command) switch (command) {
                case ADD:
                    NegotiationDialog.this.addUnitTradeItem(source,
                            (Unit)unitBox.getSelectedItem());
                    break;
                case CLEAR:
                    NegotiationDialog.this
                            .removeTradeItems(UnitTradeItem.class);
                    break;
                default:
                    logger.warning("Bad command: " + command);
                    break;
            }
        }
    }
}
