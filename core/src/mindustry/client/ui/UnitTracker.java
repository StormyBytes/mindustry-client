package mindustry.client.ui;

import arc.*;
import arc.math.geom.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;
import static mindustry.client.ClientVars.*;

public class UnitTracker extends BaseDialog {
    private static final float tagh = 42f;
    private Runnable rebuildPane = () -> {}, rebuildTags = () -> {};
    private final Seq<UnitType> selectedTags = new Seq<>();
    private Table logTable;
    {
        Events.on(EventType.ClientLoadEvent.class, event -> Core.app.post(() -> { // help this is filthy
            rebuildPane = () -> {
                int cols = Math.max((int) (Core.graphics.getWidth() / Scl.scl(400)), 1);

                logTable.clear();
                int i = 0;

                float iconSize = 64f;
                for (UnitType u : content.units()) {
                    if (selectedTags.any() && !selectedTags.contains(u)) continue;
                    for (UnitLog log : trackedUnits.get(u.id)) {
                        logTable.button(frame -> {
                            frame.table(t -> {
                                t.image(u.uiIcon).size(iconSize).expandY().align(Align.right);
                                t.table(t2 -> {
                                    t2.defaults().growX().left();
                                    t2.labelWrap(log::getIdFlag);
                                    t2.row();
                                    t2.button(b -> b.labelWrap(() -> log.getController(Core.input.shift())).left().grow(), Styles.nonet, () -> {
                                        if(!Core.input.shift() || !log.isLogicControlled()) return;
                                        if(control.input instanceof DesktopInput di) di.panning = true;
                                        //lastSentPos.set(log.getControllerX(), log.getControllerY());
                                        var controller = log.getLogicController();
                                        if(controller == null) return;
                                        Spectate.INSTANCE.spectate(log.getLogicController().controller);
                                    }).disabled(b -> !log.isLogicControlled());
                                    t2.row();
                                    t2.button(b -> b.labelWrap(log::getCoordsString).left().grow(), Styles.nonet, () -> {
                                        if(Core.input.shift()) Spectate.INSTANCE.spectate(log.getUnit() == Nulls.unit? Tmp.v5.set(log.getX(), log.getY()):log.getUnit());
                                    });
                                    t2.row();
                                    t2.label(log::getBornTime);
                                    t2.row();
                                    t2.label(log::getDeathTime);
                                    t2.row();
                                }).pad(4).width(370f - iconSize).expandY();
                            });
                        }, Styles.cleari, () -> {}).margin(5f).pad(5f).self(t -> t.update(frame -> {
                            if(frame.getHeight() > t.minHeight()) t.minHeight(frame.getHeight());
                            Vec2 bottom = frame.localToStageCoordinates(Tmp.v1.set(0,0)); //bottom left
                            t.touchable(bottom.y + frame.getHeight() < 0 || bottom.y > Core.graphics.getHeight()? Touchable.disabled:Touchable.childrenOnly);
                        }));
                        if (++i % cols == 0) {
                            logTable.row();
                        }
                    }
                }
            };

            rebuildPane.run();
        }));

        Events.on(EventType.WorldLoadEvent.class, event -> {
            if(!syncing) rebuildPane.run();
        });
    }

    public UnitTracker(){
        super("@client.unittracker");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
        setup();
    }

    void setup(){
        cont.top();
        cont.clear();

        cont.table(in -> {
            in.left();
            in.pane(Styles.nonePane, t -> {
                rebuildTags = () -> {
                    t.clearChildren();
                    t.left();

                    t.defaults().pad(2).height(tagh);
                    for(var tag : content.units()){
                        t.button(Fonts.getUnicodeStr(tag.name), Styles.togglet, () -> {
                            if(selectedTags.contains(tag)){
                                selectedTags.remove(tag);
                            }else{
                                selectedTags.add(tag);
                            }
                            rebuildPane.run();
                        }).checked(selectedTags.contains(tag)).with(c -> c.getLabel().setWrap(false));
                    }
                };
                rebuildTags.run();
            }).fillX().height(tagh).scrollY(false);
        }).height(tagh).fillX();

        cont.row();
        cont.pane(t -> {
            logTable = t;
            t.top();
            rebuildPane.run();
        }).grow().scrollX(false);
    }
}
