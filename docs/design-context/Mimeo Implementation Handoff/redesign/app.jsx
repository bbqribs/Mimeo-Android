// app.jsx — host shell that composes both visual directions side-by-side.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "showDirectionB": true,
  "showCatalog": true,
  "fontSerif": "Source Serif 4",
  "fontSans": "Inter",
  "accentA": "#B6A1FF",
  "accentB": "#C25B2E",
  "density": "regular",
  "darkA": true
}/*EDITMODE-END*/;

// ─── A live phone — keeps its own nav state ────────────────────────────────
function MimeoPhone({ T, D, initialScreen='inbox', height=820, width=380 }) {
  const [screen, setScreen] = React.useState(initialScreen);
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [nowPlaying, setNowPlaying] = React.useState(ITEMS.find(i => i.id === UP_NEXT.nowPlaying));
  const [playing, setPlaying] = React.useState(true);
  const [openItem, setOpenItem] = React.useState(null);

  // Resolve playlist key
  const plMatch = screen.match(/^pl:(\w+)$/);
  const playlist = plMatch ? PLAYLISTS.find(p => p.id === plMatch[1]) : null;

  const ctx = {
    miniH: nowPlaying ? 110 : 0,
    nowPlaying, playing,
    go: (s) => { setScreen(s); setDrawerOpen(false); },
    openMenu: () => setDrawerOpen(true),
    playItem: (it) => { setNowPlaying(it); setPlaying(true); },
    openItem: (it) => { setOpenItem(it); setScreen('reader'); },
  };

  const renderScreen = () => {
    if (screen === 'reader') return <ReaderScreen T={T} D={D} ctx={ctx} item={openItem || nowPlaying} />;
    if (screen === 'upnext') return <UpNextScreen T={T} D={D} ctx={ctx} />;
    if (screen === 'smartq') return <SmartQueueScreen T={T} D={D} ctx={ctx} />;
    if (screen === 'bluesky') return <BlueskyScreen T={T} D={D} ctx={ctx} />;
    if (screen === 'settings') return <SettingsScreen T={T} D={D} ctx={ctx} />;
    if (screen === 'privacy') return <PrivacyScreen T={T} D={D} ctx={ctx} />;
    if (playlist && playlist.kind === 'manual') return <ManualPlaylistScreen T={T} D={D} ctx={ctx} playlist={playlist} />;
    if (playlist && playlist.kind === 'smart')  return <SmartPlaylistScreen  T={T} D={D} ctx={ctx} playlist={playlist} />;
    if (['inbox','favorites','archive','bin'].includes(screen)) {
      return <InboxScreen T={T} D={D} ctx={ctx} tab={screen} />;
    }
    return <InboxScreen T={T} D={D} ctx={ctx} tab="inbox" />;
  };

  // Show jump pill if reading something other than nowPlaying
  const showJump = screen === 'reader' && openItem && nowPlaying && openItem.id !== nowPlaying.id;

  return (
    <Phone T={T} width={width} height={height}>
      <div style={{ flex:1, display:'flex', flexDirection:'column', overflow:'hidden', position:'relative' }}>
        {renderScreen()}
        {/* Drawer overlay */}
        {drawerOpen && (
          <div style={{
            position:'absolute', inset:0, zIndex:20, display:'flex',
          }}>
            <Drawer T={T} D={D} ctx={ctx} current={screen} />
            <div onClick={()=>setDrawerOpen(false)}
                 style={{ flex:1, background:'rgba(0,0,0,0.45)' }} />
          </div>
        )}
      </div>
      {nowPlaying && screen !== 'reader' && (
        <MiniPlayer T={T} item={nowPlaying} playing={playing} setPlaying={setPlaying}
          onOpen={()=>{ setOpenItem(nowPlaying); setScreen('reader'); }} />
      )}
      {nowPlaying && screen === 'reader' && (
        <MiniPlayer T={T} item={nowPlaying} playing={playing} setPlaying={setPlaying} glow
          showJump={showJump}
          onJump={()=>{ setOpenItem(nowPlaying); }} />
      )}
    </Phone>
  );
}

// ─── Design system card per direction ─────────────────────────────────────
function SystemCard({ T }) {
  const swatch = (label, val, on) => (
    <div style={{ display:'flex', alignItems:'center', gap:10 }}>
      <div style={{
        width:34, height:34, borderRadius:8, background: val,
        border: `1px solid ${T.line}`,
        display:'flex', alignItems:'center', justifyContent:'center',
        color: on || T.fg, fontFamily: T.sansFamily, fontSize:9, fontWeight:600,
      }}>{label.slice(0,2)}</div>
      <div>
        <div style={{ fontFamily:T.sansFamily, fontSize:11.5, color:T.fg, fontWeight:500 }}>{label}</div>
        <div style={{ fontFamily:'ui-monospace, Menlo, monospace', fontSize:10.5, color:T.fg3 }}>{val}</div>
      </div>
    </div>
  );
  const Block = ({ title, children }) => (
    <div>
      <div style={{
        fontFamily: T.sansFamily, fontSize:10.5, fontWeight:600, letterSpacing:'0.08em',
        textTransform:'uppercase', color: T.fg3, marginBottom:10,
      }}>{title}</div>
      {children}
    </div>
  );
  return (
    <div style={{
      width: 380, padding:'22px 22px 24px', borderRadius: T.radiusCard,
      background: T.bg, color: T.fg, border:`1px solid ${T.line}`,
    }}>
      <div style={{
        fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:18,
        color: T.fg, letterSpacing: T.titleTracking,
      }}>{T.name}</div>
      <div style={{
        fontFamily: T.sansFamily, fontSize:12, color: T.fg2, marginTop:4, lineHeight:1.5,
      }}>{T.tag}</div>
      <div style={{ height:18 }} />
      <Block title="Color roles">
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:'10px 12px' }}>
          {swatch('Background', T.bg)}
          {swatch('Surface',    T.surface)}
          {swatch('Line',       T.line)}
          {swatch('Foreground', T.fg)}
          {swatch('Muted',      T.fg2)}
          {swatch('Accent',     T.accent)}
          {swatch('Now-tint',   T.nowTint)}
          {swatch('Success',    T.success)}
        </div>
      </Block>
      <div style={{ height:18 }} />
      <Block title="Type scale">
        <div style={{ display:'flex', flexDirection:'column', gap:4 }}>
          <div style={{ fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:24, letterSpacing:T.titleTracking, color:T.fg }}>Display 24/1.2</div>
          <div style={{ fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:18, letterSpacing:T.titleTracking, color:T.fg }}>Title 18/1.3</div>
          <div style={{ fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:15, letterSpacing:T.titleTracking, color:T.fg }}>Row 15/1.32</div>
          <div style={{ fontFamily: T.sansFamily, fontSize:13, color:T.fg2 }}>Body 13/1.5 · meta + supporting copy</div>
          <div style={{ fontFamily: T.sansFamily, fontSize:11, color:T.fg3, letterSpacing:'0.08em', textTransform:'uppercase', fontWeight:600, marginTop:4 }}>SECTION 11/0.08em</div>
        </div>
      </Block>
      <div style={{ height:18 }} />
      <Block title="Card / list">
        <div style={{
          padding:'12px 14px', borderRadius: T.radiusItem, background: T.surface,
          border:`1px solid ${T.line}`,
        }}>
          <div style={{ fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:14, color:T.fg, lineHeight:1.3 }}>
            Article row · two lines maximum
          </div>
          <div style={{ fontFamily: T.sansFamily, fontSize:11.5, color:T.fg3, marginTop:4 }}>
            host · 9 min · row hairline {`(${T.lineSoft})`}
          </div>
        </div>
        <div style={{ display:'flex', gap:8, marginTop:10 }}>
          <Chip T={T} sm active>chip on</Chip>
          <Chip T={T} sm>chip off</Chip>
          <button style={{
            padding:'5px 12px', borderRadius:999, border:0, background:T.accent,
            color:T.accentOn, fontFamily:T.sansFamily, fontSize:11, fontWeight:600, cursor:'pointer',
          }}>Primary</button>
        </div>
      </Block>
      <div style={{ height:18 }} />
      <Block title="Spacing & shape">
        <div style={{ fontFamily: T.sansFamily, fontSize:12, color:T.fg2, lineHeight:1.7 }}>
          row {D_DEFAULT.rowPadV}px · section gap {D_DEFAULT.sectGap}px<br/>
          radius card {T.radiusCard} · item {T.radiusItem} · pill {T.radiusPill}
        </div>
      </Block>
    </div>
  );
}
const D_DEFAULT = DENSITY.regular;

// ─── Direction summary card (what changed / risk / build first) ───────────
function DirectionSummary({ T, summary }) {
  return (
    <div style={{
      width: 380, padding:'22px 22px 22px', borderRadius: T.radiusCard,
      background: T.bg, color: T.fg, border:`1px solid ${T.line}`,
    }}>
      <div style={{
        fontFamily: T.sansFamily, fontSize:11, fontWeight:600,
        letterSpacing:'0.08em', textTransform:'uppercase', color: T.accent, marginBottom:10,
      }}>Direction notes</div>
      {summary.map((sec, i) => (
        <div key={i} style={{ marginBottom: i < summary.length-1 ? 16 : 0 }}>
          <div style={{
            fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:14,
            color: T.fg, marginBottom:6, letterSpacing: T.titleTracking,
          }}>{sec.label}</div>
          <ul style={{ listStyle:'none', padding:0, margin:0 }}>
            {sec.items.map((it,j) => (
              <li key={j} style={{
                fontFamily: T.sansFamily, fontSize:12.5, color: T.fg2, lineHeight:1.55,
                paddingLeft:14, position:'relative', marginTop: j === 0 ? 0 : 6,
              }}>
                <span style={{
                  position:'absolute', left:0, top:9, width:4, height:4, borderRadius:2,
                  background: T.accent, opacity:.7,
                }} />
                {it}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

const SUMMARIES = {
  A: [
    { label: 'What changed', items: [
      'Drawer reorganised into Library / Listen / Playlists / Smart playlists with counts on the right.',
      'Up Next is now a single screen with anchored "Now playing" card, dimmed History, drag handles only on Up Next rows.',
      '"Re-seed Up Next" demoted to a chip, no longer a floating action.',
      'Mini-player: persistent, with progress hairline, 1.4× pill, "Jump to Now Playing" when reading another item.',
      'Bluesky: explicit Save button per candidate; Saved/Unsaved status pill; no auto-save copy in the hero band.',
    ]},
    { label: 'What stayed the same', items: [
      'Information architecture, screen names, queue concepts, and navigation drawer are unchanged.',
      'Material 3 surfaces, FAB-equivalent placements, list-row mental model.',
    ]},
    { label: 'Implementation risk', items: [
      'Low. Reads as a token + spacing refresh on top of existing Compose components.',
      'Risks: re-mapping the drawer routes; moving Up Next from FAB→chip needs IME copy review.',
    ]},
    { label: 'Build first', items: [
      'Drawer · Inbox · Up Next · Mini-player. These set the language; everything else inherits.',
    ]},
  ],
  B: [
    { label: 'What changed', items: [
      'Dark UI swapped for a warm paper surface; titles set in Source Serif; ember accent replaces lilac.',
      'Reader is the centerpiece — wider type, italic kicker, highlight color, embedded "Now playing" bridge.',
      'Up Next shifts from list-of-stuff to anchored card + soft dividers; History uses opacity not separation.',
      'Cards used for Bluesky candidates and Smart playlist rules; chips replace nested settings rows.',
      'Privacy gets a hero "What never leaves the device" panel with green checks.',
    ]},
    { label: 'What stayed the same', items: [
      'Same screens, same flows, same explicit-save policy, same Compose-buildable primitives.',
      'No mystery gestures; every action that exists today still has a discoverable button.',
    ]},
    { label: 'Implementation risk', items: [
      'Medium. Light-mode theming is new; serif title family adds a font payload.',
      'Reader and Up Next need careful hairline + spacing tokens to avoid feeling busy on dense lists.',
    ]},
    { label: 'Build first', items: [
      'Reader · Up Next · Bluesky candidate card. These three carry the visual identity.',
    ]},
  ],
};

// ─── Top header ────────────────────────────────────────────────────────────
function Header({ tweaks }) {
  return (
    <div style={{
      maxWidth: 1320, margin:'0 auto', padding:'56px 32px 24px',
      color:'#1a1a1f',
    }}>
      <div style={{
        fontFamily:'"Inter", system-ui, sans-serif', fontSize:11, fontWeight:700,
        letterSpacing:'0.18em', textTransform:'uppercase', color:'#6b6975', marginBottom:16,
      }}>Mimeo · visual redesign · 2026-05</div>
      <h1 style={{
        margin:0, fontFamily:'"Source Serif 4", Georgia, serif', fontWeight:500,
        fontSize:46, letterSpacing:'-0.015em', lineHeight:1.05, color:'#1a1a1f',
        maxWidth:880,
      }}>Two visual directions for the Android client, side&#8209;by&#8209;side.</h1>
      <p style={{
        margin:'18px 0 0', fontFamily:'"Inter", system-ui, sans-serif',
        fontSize:15.5, color:'#4f4d59', lineHeight:1.55, maxWidth:720,
      }}>
        Both phones below are interactive. Open the drawer, change tabs, play an item,
        scrub between Up Next and Reader — the prototype tracks state. Tap Tweaks in the
        toolbar to swap accents, density, and serif weighting.
      </p>
    </div>
  );
}

function SectionTitle({ kicker, title, body, dark }) {
  return (
    <div style={{
      maxWidth: 1320, margin:'0 auto', padding:'40px 32px 16px',
      color: dark ? '#ECECF1' : '#1a1a1f',
    }}>
      <div style={{
        fontFamily:'"Inter", system-ui, sans-serif', fontSize:11, fontWeight:700,
        letterSpacing:'0.18em', textTransform:'uppercase',
        color: dark ? '#9A99A6' : '#6b6975', marginBottom:10,
      }}>{kicker}</div>
      <div style={{
        fontFamily:'"Source Serif 4", Georgia, serif', fontWeight:500,
        fontSize:30, letterSpacing:'-0.01em', lineHeight:1.15,
      }}>{title}</div>
      {body && <div style={{
        fontFamily:'"Inter", system-ui, sans-serif',
        fontSize:14, color: dark ? '#9A99A6' : '#4f4d59',
        marginTop:8, lineHeight:1.55, maxWidth:680,
      }}>{body}</div>}
    </div>
  );
}

// ─── Screen catalog (small phones) ────────────────────────────────────────
function ScreenCatalog({ T }) {
  const SCREENS = [
    { k:'inbox',     l:'Inbox' },
    { k:'upnext',    l:'Up Next' },
    { k:'reader',    l:'Reader' },
    { k:'smartq',    l:'Smart Queue' },
    { k:'pl:p1',     l:'Manual playlist' },
    { k:'pl:p4',     l:'Smart playlist' },
    { k:'bluesky',   l:'Bluesky' },
    { k:'settings',  l:'Settings' },
    { k:'privacy',   l:'Privacy' },
  ];
  const W = 240, H = 520, scale = W/380;
  return (
    <div style={{
      display:'grid', gridTemplateColumns:`repeat(auto-fill, minmax(${W}px, 1fr))`,
      gap:24, padding:'8px 32px 48px', maxWidth:1320, margin:'0 auto',
    }}>
      {SCREENS.map(s => (
        <div key={s.k}>
          <div style={{
            width:W, height:H, position:'relative', overflow:'hidden',
            margin:'0 auto', pointerEvents:'none',
          }}>
            <div style={{
              transform:`scale(${scale})`, transformOrigin:'top left',
              width: 380, height: 380*(H/W),
            }}>
              <MimeoPhone T={T} D={DENSITY.regular} initialScreen={s.k} width={380} height={H/scale} />
            </div>
          </div>
          <div style={{
            textAlign:'center', marginTop:12,
            fontFamily:'"Inter", system-ui, sans-serif', fontSize:12.5, color:'#6b6975',
            fontWeight:500,
          }}>{s.l}</div>
        </div>
      ))}
    </div>
  );
}

// ─── Tweaks panel ─────────────────────────────────────────────────────────
function MimeoTweaks({ tweaks, setTweak }) {
  return (
    <TweaksPanel title="Tweaks">
      <TweakSection title="Layout">
        <TweakToggle  label="Show Direction B"      value={tweaks.showDirectionB} onChange={v=>setTweak('showDirectionB', v)} />
        <TweakToggle  label="Show screen catalog"   value={tweaks.showCatalog}    onChange={v=>setTweak('showCatalog', v)} />
        <TweakRadio   label="Density"               value={tweaks.density} onChange={v=>setTweak('density', v)}
                      options={[{value:'compact',label:'Compact'},{value:'regular',label:'Regular'},{value:'comfy',label:'Comfy'}]}/>
      </TweakSection>
      <TweakSection title="Direction A · accent">
        <TweakColor   label="Accent"  value={tweaks.accentA} onChange={v=>setTweak('accentA', v)}
                      options={['#B6A1FF','#8FB6FF','#7FD1A8','#E8C26A','#F2A1B6']} />
        <TweakToggle  label="Dark surface" value={tweaks.darkA} onChange={v=>setTweak('darkA', v)} />
      </TweakSection>
      <TweakSection title="Direction B · accent">
        <TweakColor   label="Accent"  value={tweaks.accentB} onChange={v=>setTweak('accentB', v)}
                      options={['#C25B2E','#3F7A52','#2B567A','#7A3E8C','#1B1A17']} />
      </TweakSection>
      <TweakSection title="Type">
        <TweakSelect label="Sans family"  value={tweaks.fontSans} onChange={v=>setTweak('fontSans', v)}
                     options={['Inter','IBM Plex Sans','Geist','Manrope','system-ui']}/>
        <TweakSelect label="Serif family" value={tweaks.fontSerif} onChange={v=>setTweak('fontSerif', v)}
                     options={['Source Serif 4','Fraunces','Spectral','Crimson Pro','Lora']}/>
      </TweakSection>
    </TweaksPanel>
  );
}

// ─── App ──────────────────────────────────────────────────────────────────
function App() {
  const [tweaks, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const D = DENSITY[tweaks.density] || DENSITY.regular;

  // Apply tweaks to tokens
  const T_A = React.useMemo(() => {
    const base = { ...TOKENS.A,
      accent: tweaks.accentA,
      accentDim: hexAlpha(tweaks.accentA, 0.14),
      nowEdge: tweaks.accentA,
      nowTint: hexAlpha(tweaks.accentA, 0.06),
      sansFamily: `"${tweaks.fontSans}", system-ui, sans-serif`,
      titleFamily: `"${tweaks.fontSans}", system-ui, sans-serif`,
      serifFamily: `"${tweaks.fontSerif}", Georgia, serif`,
    };
    if (!tweaks.darkA) {
      // light variant of A
      Object.assign(base, {
        bg:'#FAFAFC', surface:'#FFFFFF', surfaceHi:'#F2F1F7',
        line:'#E7E5EE', lineSoft:'#F0EFF5',
        fg:'#1A1A22', fg2:'#5E5D6B', fg3:'#9A99A6', fg4:'#C9C7D2',
        drawerBg:'#F5F4F9', appbarBg:'#FAFAFC', miniPlayerBg:'#F5F4F9', miniPlayerLine:'#E7E5EE',
        accentOn:'#FFFFFF',
      });
    }
    return base;
  }, [tweaks.accentA, tweaks.fontSans, tweaks.fontSerif, tweaks.darkA]);

  const T_B = React.useMemo(() => ({
    ...TOKENS.B,
    accent: tweaks.accentB,
    accentDim: hexAlpha(tweaks.accentB, 0.10),
    nowEdge: tweaks.accentB,
    nowTint: hexAlpha(tweaks.accentB, 0.07),
    sansFamily: `"${tweaks.fontSans}", system-ui, sans-serif`,
    titleFamily: `"${tweaks.fontSerif}", Georgia, serif`,
    serifFamily: `"${tweaks.fontSerif}", Georgia, serif`,
  }), [tweaks.accentB, tweaks.fontSans, tweaks.fontSerif]);

  return (
    <div style={{ minHeight:'100vh', background:'#F4EFE7', paddingBottom:80 }}>
      <Header tweaks={tweaks} />
      {/* Prototype row */}
      <div style={{
        display:'grid', gridTemplateColumns: tweaks.showDirectionB ? '1fr 1fr' : '1fr',
        gap: 0, maxWidth:1320, margin:'0 auto', padding:'24px 0 0',
      }}>
        {/* Direction A */}
        <DirectionPanel
          T={T_A} D={D} kicker="Direction A" subtitle={T_A.name}
          tag={TOKENS.A.tag}
          summary={SUMMARIES.A}
          dark={tweaks.darkA}
        />
        {/* Direction B */}
        {tweaks.showDirectionB && (
          <DirectionPanel
            T={T_B} D={D} kicker="Direction B" subtitle={T_B.name}
            tag={TOKENS.B.tag}
            summary={SUMMARIES.B}
            dark={false}
          />
        )}
      </div>
      {/* Catalog */}
      {tweaks.showCatalog && (
        <>
          <SectionTitle kicker="Direction A · Calm Lilac" title="Screen catalog"
            body="Every screen at a single glance. Each thumbnail is a live render — the prototype renders the same components used by the interactive phones above." />
          <div style={{ background:'#0B0B0E', padding:'20px 0 0', borderRadius:0 }}>
            <ScreenCatalog T={T_A} />
          </div>
          {tweaks.showDirectionB && (
            <>
              <SectionTitle kicker="Direction B · Paper & Ember" title="Screen catalog" />
              <ScreenCatalog T={T_B} />
            </>
          )}
        </>
      )}
      <MimeoTweaks tweaks={tweaks} setTweak={setTweak} />
    </div>
  );
}

function DirectionPanel({ T, D, kicker, subtitle, tag, summary, dark }) {
  return (
    <div style={{
      padding:'40px 32px 32px',
      background: dark ? '#0B0B0E' : '#F4EFE7',
      color: dark ? '#ECECF1' : '#1a1a1f',
      borderTop:`1px solid ${dark ? '#1F1F2A' : '#E2DACB'}`,
    }}>
      <div style={{
        fontFamily:'"Inter", system-ui, sans-serif', fontSize:11, fontWeight:700,
        letterSpacing:'0.18em', textTransform:'uppercase',
        color: T.accent, marginBottom:8,
      }}>{kicker}</div>
      <div style={{
        fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:26,
        letterSpacing: T.titleTracking, lineHeight:1.15, color: dark ? '#ECECF1' : '#1a1a1f',
      }}>{subtitle}</div>
      <div style={{
        fontFamily:'"Inter", system-ui, sans-serif', fontSize:13, marginTop:6,
        color: dark ? '#9A99A6' : '#4f4d59', maxWidth:380,
      }}>{tag}</div>
      <div style={{ display:'flex', gap:24, marginTop:28, flexWrap:'wrap' }}>
        <MimeoPhone T={T} D={D} initialScreen="upnext" />
        <div style={{ display:'flex', flexDirection:'column', gap:20, width:380 }}>
          <DirectionSummary T={T} summary={summary} />
          <SystemCard T={T} />
        </div>
      </div>
    </div>
  );
}

// ─── color util ────────────────────────────────────────────────────────────
function hexAlpha(hex, a) {
  const h = hex.replace('#','');
  const n = h.length === 3
    ? h.split('').map(c=>c+c).join('')
    : h;
  const r = parseInt(n.slice(0,2),16), g = parseInt(n.slice(2,4),16), b = parseInt(n.slice(4,6),16);
  return `rgba(${r},${g},${b},${a})`;
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
