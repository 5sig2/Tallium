import json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
matrix=json.loads((ROOT/'versions.json').read_text())
for target in matrix['targets']:
    v=target['minecraft']; modern=v.startswith('26.'); minor=99 if modern else (int(v.split('.')[2]) if v.count('.')==2 else 0)
    version_dir=ROOT/'versions'/v;version_dir.mkdir(parents=True,exist_ok=True)
    plugin='net.fabricmc.fabric-loom-remap' if target['pipeline']=='remap' else 'net.fabricmc.fabric-loom'
    (version_dir/'build.gradle').write_text("plugins { id '"+plugin+"' }\napply from: rootProject.file('tools/adapter.gradle')\n",encoding='utf-8')
    identifier='Identifier' if minor>=11 else 'ResourceLocation'
    category=f'KeyMapping.Category.register({identifier}.fromNamespaceAndPath("tallium","keys"))' if minor>=9 else '"key.categories.tallium"'
    def key(name,code): return f'new KeyMapping("key.tallium.{name}",InputConstants.Type.KEYSYM,{code},CATEGORY)'
    state='net.minecraft.client.renderer.entity.state.EntityRenderState'
    camera='net.minecraft.client.renderer.state.level.CameraRenderState' if modern else 'net.minecraft.client.renderer.state.CameraRenderState'
    rendertype='net.minecraft.client.renderer.rendertype.RenderTypes' if minor>=11 else 'net.minecraft.client.renderer.RenderType'
    if minor<9:
        tag_hooks = """@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final protected net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher;
        private void tallium$draw(dev.sig.tallium.adapter.CustomLabels.Anchor anchor,boolean discrete,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
            if(anchor==null)return;var layout=anchor.layout();var attachment=anchor.attachment();var mc=Minecraft.getInstance();pose.pushPose();pose.translate(attachment.x,attachment.y+0.5,attachment.z);pose.mulPose(entityRenderDispatcher.cameraOrientation());float scale=(float)(.025*dev.sig.tallium.adapter.TalliumClient.config.active().nametagScale);pose.scale(scale,-scale,scale);
            for(Tags.Row row:layout.rows()){float left=row.x();int background=(int)(mc.options.getBackgroundOpacity(.25f)*255)<<24;
            mc.font.drawInBatch(row.text(),left,0,0x80ffffff,false,pose.last().pose(),buffers,discrete?net.minecraft.client.gui.Font.DisplayMode.NORMAL:net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,background,light);
            if(!discrete)mc.font.drawInBatch(row.text(),left,0,-1,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);
            for(Tags.Icon icon:row.icons()){var texture=VanillaIcons.texture(icon.stack());if(texture!=null)tallium$quad(pose.last(),buffers.getBuffer(discrete?net.minecraft.client.renderer.RenderType.text(texture):net.minecraft.client.renderer.RenderType.textSeeThrough(texture)),left+icon.x(),light);}
            }pose.popPose();
        }
        """
        if minor<2:
            tag_hooks += """@org.spongepowered.asm.mixin.Shadow protected abstract boolean shouldShowName(Entity entity);
            @Inject(method="render",at=@At("TAIL"))
            private void tallium$independent(Entity entity,float yaw,float delta,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,CallbackInfo ci){
                if(entity instanceof Player player)tallium$draw(dev.sig.tallium.adapter.CustomLabels.anchor(player,shouldShowName(player),delta),entity.isDiscrete(),pose,buffers,light);
            }"""
        else:
            tag_hooks += f'''private final java.util.Map<Object,dev.sig.tallium.adapter.CustomLabels.Anchor> tallium$anchors=new java.util.WeakHashMap<>();
            @Inject(method="extractRenderState",at=@At("TAIL"))
            private void tallium$extract(Entity entity,{state} state,float delta,CallbackInfo ci){{
                tallium$anchors.remove(state);if(entity instanceof Player player){{var anchor=dev.sig.tallium.adapter.CustomLabels.anchor(player,state.nameTag!=null,delta);if(anchor!=null)tallium$anchors.put(state,anchor);}}
            }}
            @Inject(method="render",at=@At("TAIL"))
            private void tallium$independent({state} state,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,CallbackInfo ci){{tallium$draw(tallium$anchors.get(state),state.isDiscrete,pose,buffers,light);}}'''
    else:
        tag_hooks=f'''private final java.util.Map<Object,dev.sig.tallium.adapter.CustomLabels.Anchor> tallium$anchors=new java.util.WeakHashMap<>();
        @Inject(method="extractRenderState",at=@At("TAIL"))
        private void tallium$extract(Entity entity,{state} state,float delta,CallbackInfo ci){{
            tallium$anchors.remove(state);if(entity instanceof Player player){{var anchor=dev.sig.tallium.adapter.CustomLabels.anchor(player,state.nameTag!=null,delta);if(anchor!=null)tallium$anchors.put(state,anchor);}}
        }}
        @Inject(method="submit",at=@At("TAIL"))
        private void tallium$independent({state} state,PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,{camera} camera,CallbackInfo ci){{
            var anchor=tallium$anchors.get(state);if(anchor==null)return;var a=anchor.attachment();pose.pushPose();pose.translate(a.x,a.y+.5,a.z);float scale=(float)dev.sig.tallium.adapter.TalliumClient.config.active().nametagScale;pose.scale(scale,scale,scale);
            for(Tags.Row row:anchor.layout().rows()){{var part=new Tags.Layout(row.text(),row.icons(),0,0);Tags.remember(part);pose.pushPose();var shift=new org.joml.Vector3f(row.x()*.025f,0,0).rotate(camera.orientation);pose.translate(shift.x(),shift.y(),shift.z());collector.submitNameTag(pose,new net.minecraft.world.phys.Vec3(0,-.5,0),0,part.text(),!state.isDiscrete,state.lightCoords,{"" if v=="26.2" else "state.distanceToCameraSq,"}camera);pose.popPose();}}pose.popPose();
        }}'''
    if minor>=5:
        draw='WorldItems.state(icon.stack()).submit(pose,collector,state.lightCoords,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);' if minor>=9 else 'WorldItems.state(icon.stack()).render(pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);'
    else:
        draw='Minecraft.getInstance().getItemRenderer().renderStatic(icon.stack(),net.minecraft.world.item.ItemDisplayContext.GUI,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,Minecraft.getInstance().level,0);'

    tag_hooks=tag_hooks.replace('at=@At("TAIL")','at=@At("RETURN")')
    tag_hooks=tag_hooks.replace('for(Tags.Icon icon:row.icons()){var texture=', 'for(Tags.Icon icon:row.icons()){if(icon.mode().equals("RESOURCE_PACK")){pose.pushPose();pose.translate(left+icon.x()+4,4,0);pose.scale(8,-8,8);'+draw+'pose.popPose();continue;}var texture=')
    values={'GAME':v,'IDENTIFIER':identifier,'VANILLA_TOTEM':'true' if minor<2 else 'false',
        'PLAYER_INVENTORY_HOOK':'@Inject(method="handleSetPlayerInventory",at=@At("TAIL")) private void tallium$playerInventory(ClientboundSetPlayerInventoryPacket packet,CallbackInfo ci){TalliumClient.authoritativeSlot(packet.slot(),packet.contents());}' if minor>=2 else '',
        'CONTAINER_ID':'containerId' if minor>=5 else 'getContainerId',
        'CONTAINER_ITEMS':'items' if minor>=5 else 'getItems',
        'MENU_STACK':'''if(item.builtInRegistryHolder().areComponentsBound())return new ItemStack(item);
        var id=BuiltInRegistries.ITEM.getKey(item);
        var components=net.minecraft.core.component.DataComponentMap.builder()
            .set(DataComponents.ITEM_NAME,Component.translatable((item instanceof net.minecraft.world.item.BlockItem?"block.":"item.")+id.getNamespace()+"."+id.getPath().replace('/','.')))
            .set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.ITEM_MODEL,id).build();
        return new ItemStack(net.minecraft.core.Holder.direct(item,components),1);''' if modern else 'return new ItemStack(item);',
        'KEY_CATEGORY_ORDER':'@Accessor("CATEGORY_SORT_ORDER") static Map<String,Integer> tallium$categoryOrder(){throw new AssertionError();}' if minor<9 else '',
        'REGISTER_KEY_CATEGORY':'var categoryOrder=dev.sig.tallium.mixin.KeyMappingAccessor.tallium$categoryOrder();categoryOrder.computeIfAbsent(CATEGORY,ignored->categoryOrder.values().stream().mapToInt(Integer::intValue).max().orElse(0)+1);' if minor<9 else '',
        'CAMERA_POSITION':'position' if minor>=11 else 'getPosition',
        'CAMERA_FORWARD':'forwardVector' if minor>=11 else 'getLookVector',
        'MAIN_CAMERA':'mainCamera' if v=='26.2' else 'getMainCamera',
        'KEY_OPEN':key('open','301'),'KEY_INFO':key('info','86'),'KEY_RESET':key('reset','299'),
        'KEY_CUSTOM':'new KeyMapping("key.tallium.action."+id,InputConstants.Type.KEYSYM,-1,CATEGORY)',
        'POSE_PUSH':'graphics.pose().pushMatrix();' if minor>=6 else 'graphics.pose().pushPose();',
        'POSE_POP':'graphics.pose().popMatrix();' if minor>=6 else 'graphics.pose().popPose();',
        'POSE_TRANSLATE':'graphics.pose().translate(x,y);' if minor>=6 else 'graphics.pose().translate(x,y,0);',
        'POSE_SCALE':'graphics.pose().scale((float)renderScale,(float)renderScale);' if minor>=6 else 'graphics.pose().scale((float)renderScale,(float)renderScale,1);',
        'POTION_CATALOGUE':'''var registry=mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.POTION);
        registry.listElements().forEach(holder->{for(Item item:List.of(Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION,Items.TIPPED_ARROW)){
            ItemStack stack=new ItemStack(item);stack.set(DataComponents.POTION_CONTENTS,new PotionContents(holder));catalogue.add(stack);
            representatives.put(BuiltInRegistries.ITEM.getKey(item)+"|"+holder.unwrapKey().orElseThrow().identifier(),stack);}});''',
        'SCREEN_OWNER':'net.minecraft.client.gui.Gui' if v=='26.2' else 'Minecraft',
        'CAPTURE_REFERENCE':('Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->accept(image,request,world,referenceWidth,referenceHeight));' if v=='26.2' else 'Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->accept(image,request,world,referenceWidth,referenceHeight));') if minor>=5 else 'accept(Screenshot.takeScreenshot(mc.getMainRenderTarget()),request,world,referenceWidth,referenceHeight);',
        'REFERENCE_BLIT':('g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,0,0,0,0,width,height,1,1,1,1);' if minor>=6 else 'g.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,0,0,0,0,width,height,1,1,1,1);' if minor>=2 else 'g.blit(id,0,0,width,height,0,0,1,1,1,1);'),
        'SKIN_TEXTURE':'skin.body().texturePath()' if minor>=9 else 'skin.texture()',
        'PORTRAIT_PARTS':'getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION,(byte)127);' if minor<9 else '',
        'PORTRAIT_DRAW':'extractEntityInInventoryFollowsMouse' if modern else 'renderEntityInInventoryFollowsMouse',
        'PORTRAIT_FALLBACK':('graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,dx,dy,8,8,size,size,8,8,64,64);graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,dx,dy,40,8,size,size,8,8,64,64);' if minor>=6 else 'graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,dx,dy,8,8,size,size,8,8,64,64);graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,dx,dy,40,8,size,size,8,8,64,64);' if minor>=2 else 'graphics.blit(id,dx,dy,size,size,8,8,8,8,64,64);graphics.blit(id,dx,dy,size,size,40,8,8,8,64,64);'),
        'POPUP_HEAD':('graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,8,8,8,8,24,24,8,8,64,64);graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,8,8,40,8,24,24,8,8,64,64);' if minor>=6 else 'graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,8,8,8,8,24,24,8,8,64,64);graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,8,8,40,8,24,24,8,8,64,64);' if minor>=2 else 'graphics.blit(id,8,8,24,24,8,8,8,8,64,64);graphics.blit(id,8,8,24,24,40,8,8,8,64,64);'),
        'ICON_TRANSLATE':'g.pose().translate(iconX,y);' if minor>=6 else 'g.pose().translate(iconX,y,0);',
        'DYNAMIC_TEXTURE':'new DynamicTexture(()->"Tallium vanilla icon",image)' if minor>=5 else 'new DynamicTexture(image)',
        'BLIT':'g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x,y,0,0,16,16,16,16);' if minor>=6 else ('g.blit(net.minecraft.client.renderer.RenderType::guiTextured,id,x,y,0,0,16,16,16,16);' if minor>=2 else 'g.blit(id,x,y,0,0,16,16,16,16);'),
        'PIXEL_GET':'getPixel' if minor>=2 else 'getPixelRGBA',
        'PIXEL_SET':'setPixel' if minor>=2 else 'setPixelRGBA',
        'PIXEL_TO_ARGB':'color' if minor>=2 else '(color&0xff00ff00)|((color&255)<<16)|((color>>>16)&255)',
        'PIXEL_FROM_ARGB':'argb' if minor>=2 else '(argb&0xff00ff00)|((argb&255)<<16)|((argb>>>16)&255)',
        'TAG_HOOKS':tag_hooks,
        'COLLECTOR_TARGET':('net.minecraft.client.renderer.SubmitNodeStorage' if v=='26.2' else 'net.minecraft.client.renderer.SubmitNodeCollection') if minor>=9 else 'net.minecraft.client.renderer.entity.EntityRenderer',
        'COLLECTOR_HOOK':f'''{' ' if v=='26.2' else '@org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.renderer.SubmitNodeStorage submitNodeStorage;'}
        @Inject(method="submitNameTag",at=@At("HEAD"))
        private void tallium$center(PoseStack pose,net.minecraft.world.phys.Vec3 attachment,int offset,Component text,boolean seeThrough,int light,{"" if v=="26.2" else "double distance,"}{camera} camera,CallbackInfo ci){{
            var info=Tags.renderInfo(text);if(info==null||info.icons().isEmpty()||attachment==null)return;
            pose.pushPose();float shift=(Minecraft.getInstance().font.width(text)-info.nameWidth())/2f-info.nameStart();
            var translation=new org.joml.Vector3f(shift*.025f,0,0).rotate(camera.orientation);pose.translate(translation.x,translation.y,translation.z);
        }}
        @Inject(method="submitNameTag",at=@At("TAIL"))
        private void tallium$icons(PoseStack pose,net.minecraft.world.phys.Vec3 attachment,int offset,Component text,boolean seeThrough,int light,{"" if v=="26.2" else "double distance,"}{camera} camera,CallbackInfo ci){{
            var info=Tags.renderInfo(text);if(info==null||info.icons().isEmpty()||attachment==null)return;
            var icons=info.icons();var collector=(net.minecraft.client.renderer.OrderedSubmitNodeCollector)(Object)this;
            pose.pushPose();pose.translate(attachment.x,attachment.y+0.5,attachment.z);pose.mulPose(camera.orientation);pose.scale(.025f,-.025f,.025f);pose.translate(0,offset,0);
            float left=-Minecraft.getInstance().font.width(text)/2f;
            for(Tags.Icon icon:icons){{if(icon.mode().equals("RESOURCE_PACK")){{pose.pushPose();pose.translate(left+icon.x()+4,4,0);pose.scale(8,-8,8);WorldItems.state(icon.stack()).submit(pose,{"(net.minecraft.client.renderer.SubmitNodeCollector)(Object)this" if v=="26.2" else "submitNodeStorage"},light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);pose.popPose();continue;}}
                var texture=VanillaIcons.texture(icon.stack());if(texture!=null)collector.submitCustomGeometry(pose,seeThrough?{rendertype}.textSeeThrough(texture):{rendertype}.text(texture),(p,vertices)->tallium$quad(p,vertices,left+icon.x(),light));}}
            pose.popPose();pose.popPose();
        }}''' if minor>=9 else '',

        'PROFILE_UUID':'id' if minor>=9 else 'getId',
        'SELECTED_SLOT':'mc.player.getInventory().getSelectedSlot()' if minor>=5 else 'mc.player.getInventory().selected',
        'VISIBLE_TOTEM':'java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).filter(s->s.has(DataComponents.DEATH_PROTECTION)).findFirst().map(s->s.is(Items.TOTEM_OF_UNDYING)).orElse(false)' if minor>=2 else 'true',
        'XP_OFFSET':'29' if minor>=9 else '32',
        'WORLD_ITEMS_CLEAR':'WorldItems.clear();' if minor>=5 else '',
        'TAB_RENDER':'extractRenderState' if modern else 'render',
        'TAB_DRAW_TARGET':f'Lnet/minecraft/client/gui/GuiGraphics;{"text" if modern else "drawString"}(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III){"V" if minor>=6 else "I"}',
        'DRAW_RETURN_TYPE':'void' if minor>=6 else 'int',
        'DRAW_ORIGINAL':'g.drawString(font,text,x,y,color);' if minor>=6 else 'int result=g.drawString(font,text,x,y,color);',
        'DRAW_RETURN':'' if minor>=6 else 'return result;',
        'TAB_POSE_PUSH':'g.pose().pushMatrix();' if minor>=6 else 'g.pose().pushPose();',
        'TAB_POSE_POP':'g.pose().popMatrix();' if minor>=6 else 'g.pose().popPose();',
        'TAB_POSE_TRANSLATE':'g.pose().translate(x+icon.x(),y);' if minor>=6 else 'g.pose().translate(x+icon.x(),y,0);',
        'TAB_POSE_SCALE':'g.pose().scale(.5f,.5f);' if minor>=6 else 'g.pose().scale(.5f,.5f,1);',
        'ENTRY_PUSH':'g.pose().pushMatrix();' if minor>=6 else 'g.pose().pushPose();',
        'ENTRY_POP':'g.pose().popMatrix();' if minor>=6 else 'g.pose().popPose();',
        'ENTRY_TRANSLATE':'g.pose().translate(x,y);' if minor>=6 else 'g.pose().translate(x,y,0);',
        'ENTRY_SCALE':'g.pose().scale(group.appearance.iconSize/16f,group.appearance.iconSize/16f);' if minor>=6 else 'g.pose().scale(group.appearance.iconSize/16f,group.appearance.iconSize/16f,1);',
        'TEXT_TRANSLATE':'g.pose().translate(textX,textY);' if minor>=6 else 'g.pose().translate(textX,textY,0);',
        'TEXT_SCALE':'g.pose().scale((float)group.appearance.textScale,(float)group.appearance.textScale);' if minor>=6 else 'g.pose().scale((float)group.appearance.textScale,(float)group.appearance.textScale,1);',
        'CLICK_SIGNATURE':'net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick' if minor>=9 else 'double mouseX,double mouseY,int button',
        'CLICK_VARS':'double mouseX=event.x(),mouseY=event.y();int button=event.button();' if minor>=9 else '',
        'CLICK_SUPER':'event,doubleClick' if minor>=9 else 'mouseX,mouseY,button',
        'DRAG_SIGNATURE':'net.minecraft.client.input.MouseButtonEvent event,double dx,double dy' if minor>=9 else 'double mouseX,double mouseY,int button,double dx,double dy',
        'DRAG_VARS':'double mouseX=event.x(),mouseY=event.y();int button=event.button();' if minor>=9 else '',
        'DRAG_SUPER':'event,dx,dy' if minor>=9 else 'mouseX,mouseY,button,dx,dy',
        'KEY_SIGNATURE':'net.minecraft.client.input.KeyEvent event' if minor>=9 else 'int key,int scan,int modifiers',
        'KEY_VARS':'int key=event.key();' if minor>=9 else '',
        'KEY_SUPER':'event' if minor>=9 else 'key,scan,modifiers',
        'WIDGET_RENDER':'extractWidgetRenderState' if modern else 'renderWidget',
        'BUTTON_RENDER':'extractContents' if modern else ('renderContents' if minor>=11 else 'renderWidget'),
        'RELEASE_SIGNATURE':'net.minecraft.client.input.MouseButtonEvent event' if minor>=9 else 'double mouseX,double mouseY,int button',
        'RELEASE_VARS':'int button=event.button();' if minor>=9 else '',
        'RELEASE_SUPER':'event' if minor>=9 else 'mouseX,mouseY,button',
    }
    dest=ROOT/'versions'/v/'src/main/java/dev/sig/tallium'
    for template in (ROOT/'adapters/template').glob('*.java'):
        if template.name=='WorldItems.java' and minor<5:continue
        text=template.read_text(encoding='utf-8')

        text=text.replace('renderBackground(g,x,y,delta);','g.fill(0,0,width,height,0xc0202020);').replace('renderBackground(g,mx,my,delta);','g.fill(0,0,width,height,0xc0202020);')
        for unused in range(2):
            for k,value in values.items():text=text.replace('@'+k+'@',value)
        if template.name=='TalliumClient.java':
            text=text.replace('public static final KeyMapping OPEN=',f'private static final {"KeyMapping.Category" if minor>=9 else "String"} CATEGORY={category};\n    public static final KeyMapping OPEN=')
        if minor<11:text=text.replace('.identifier()', '.location()')
        if modern:
            if template.name=="CustomLabels.java":
                for old,new in [("getScale()","scale()"),("getTranslation()","translation()"),("getLeftRotation()","leftRotation()"),("getRightRotation()","rightRotation()")]:text=text.replace(old,new)
            text=text.replace('charged.getItems()', 'charged.itemCopies()')
            text=text.replace('GuiGraphics','GuiGraphicsExtractor').replace('.drawString(','.text(').replace('.renderItem(','.item(')
            text=text.replace('public void render(', 'public void extractRenderState(').replace('super.render(', 'super.extractRenderState(')
            text=text.replace('renderBackground(', 'extractBackground(').replace('mc.gui.getChat().addMessage(', 'mc.gui.getChat().addClientSystemMessage(')
            text=text.replace('mc.player.displayClientMessage(Component.literal("Tallium · "+text),true)', 'mc.player.sendOverlayMessage(Component.literal("Tallium · "+text))')
            if template.name=='GuiMixin.java':text=text.replace('method="render"','method="extractRenderState"')
        if v=='26.2':
            text=text.replace('mc.setScreen(','mc.gui.setScreen(').replace('minecraft.setScreen(','minecraft.gui.setScreen(').replace('mc.screen','mc.gui.screen()').replace('mc.gui.getChat()', 'mc.gui.hud.getChat()')
            text=text.replace('mc.options.hideGui','mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden')
            if template.name=='GuiMixin.java':text=text.replace('@Mixin(Gui.class)','@Mixin(Hud.class)').replace('method="render"','method="extractRenderState"')
        package='mixin' if 'package dev.sig.tallium.mixin;' in text else 'adapter'
        (dest/package).mkdir(parents=True,exist_ok=True);(dest/package/template.name).write_text(text,encoding='utf-8')
