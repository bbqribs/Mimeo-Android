// screens.jsx — full-screen compositions for Mimeo redesign.
// Each takes { T, D, ctx } where ctx has { go, openMenu, etc. }

const HOSTS = ['twz.com','forbes.com','wired.com','nytimes.com','ft.com','ballsandstrikes.org'];

// ─── Drawer (overlay) ─────────────────────────────────────────────────────
function Drawer({ T, D, ctx, current }) {
  const item = (key, icon, label, count) => {
    const on = current === key;
    return (
      <div key={key} onClick={()=>ctx.go(key)} style={{
        display:'flex', alignItems:'center', gap:14,
        padding:'12px 18px', margin:'2px 10px', borderRadius:12,
        background: on ? T.drawerSel : 'transparent', cursor:'pointer',
        color: on ? T.accent : T.fg, position:'relative',
      }}>
        <div style={{ color: on ? T.accent : T.fg2, display:'flex' }}>
          {React.cloneElement(icon, { size:20 })}
        </div>
        <div style={{
          flex:1, fontFamily: T.sansFamily, fontSize:15,
          fontWeight: on ? 600 : 500, letterSpacing:'-0.005em',
        }}>{label}</div>
        {count != null && (
          <div style={{
            fontFamily: T.sansFamily, fontSize:12, color: T.fg3, fontVariantNumeric:'tabular-nums',
          }}>{count}</div>
        )}
      </div>
    );
  };
  const sectLabel = (l) => (
    <div style={{
      padding:'18px 28px 6px', fontFamily: T.sansFamily, fontSize:10.5,
      fontWeight:600, letterSpacing:'0.1em', textTransform:'uppercase', color: T.fg3,
    }}>{l}</div>
  );
  return (
    <div style={{ width:288, height:'100%', background: T.drawerBg, display:'flex', flexDirection:'column' }}>
      <div style={{ padding:'24px 28px 8px', display:'flex', alignItems:'center', gap:10 }}>
        <div style={{
          width:30, height:30, borderRadius:8, background: T.accent, color: T.accentOn,
          display:'flex', alignItems:'center', justifyContent:'center',
          fontFamily: T.titleFamily, fontWeight:700, fontSize:16,
        }}>m</div>
        <div style={{
          fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:20,
          letterSpacing: T.titleTracking, color: T.fg,
        }}>Mimeo</div>
      </div>
      <div style={{ height:14 }} />
      {sectLabel('Library')}
      {item('inbox',     I.inbox, 'Inbox',     14)}
      {item('favorites', I.heart, 'Favorites',  3)}
      {item('archive',   I.archive,'Archive',  82)}
      {item('bin',       I.trash, 'Bin',        4)}
      {sectLabel('Listen')}
      {item('upnext',    I.queue, 'Up Next',   '99')}
      {item('smartq',    I.smart, 'Smart Queue')}
      {item('bluesky',   I.bsky,  'Bluesky')}
      {sectLabel('Playlists')}
      {PLAYLISTS.filter(p=>p.kind==='manual').map(p =>
        item('pl:'+p.id, I.list, p.name, p.count))}
      <div style={{ height:6 }} />
      {sectLabel('Smart playlists')}
      {PLAYLISTS.filter(p=>p.kind==='smart').map(p =>
        item('pl:'+p.id, I.diamond, p.name, p.count))}
      <div style={{ flex:1 }} />
      <div style={{ borderTop:`1px solid ${T.line}`, padding:'8px 0' }}>
        {item('settings', I.cog, 'Settings')}
      </div>
    </div>
  );
}

// ─── Inbox / Library ──────────────────────────────────────────────────────
function InboxScreen({ T, D, ctx, tab='inbox' }) {
  const TABS = [
    { k:'inbox', l:'Inbox', n: 14 },
    { k:'favorites', l:'Favorites', n: 3 },
    { k:'archive', l:'Archive', n: 82 },
    { k:'bin', l:'Bin', n: 4 },
  ];
  const list = tab === 'favorites' ? ITEMS.filter(i=>i.fav)
            : tab === 'archive'  ? ITEMS.slice(8,12)
            : tab === 'bin'      ? ITEMS.slice(12,14)
            : ITEMS.slice(0,9);

  return (
    <>
      <AppBar T={T} title={TABS.find(t=>t.k===tab).l} onMenu={ctx.openMenu}
        right={<><IconBtn T={T} icon={I.search}/><IconBtn T={T} icon={I.more}/></>} />
      <div style={{
        padding:'10px 14px 10px', display:'flex', gap:6, overflowX:'auto',
        background: T.appbarBg, borderBottom:`1px solid ${T.lineSoft}`,
        scrollbarWidth:'none',
      }}>
        {TABS.map(tt => (
          <Chip key={tt.k} T={T} active={tt.k===tab} onClick={()=>ctx.go(tt.k)} sm>
            {tt.l} <span style={{ opacity:.55, marginLeft:4 }}>{tt.n}</span>
          </Chip>
        ))}
      </div>
      <ScrollBody T={T} padBottom={ctx.miniH}>
        {list.length === 0 ? (
          <EmptyState T={T} icon={I.inbox} title="Nothing here yet"
            body="Save articles from anywhere — share to Mimeo, paste a URL, or scan from Bluesky." />
        ) : (
          <>
            <div style={{ padding:'10px 18px 4px' }}>
              <div style={{
                fontFamily: T.sansFamily, fontSize:11.5, color: T.fg3,
                display:'flex', alignItems:'center', gap:6,
              }}>
                <span>{list.length} items</span>
                <span style={{ color: T.fg4 }}>·</span>
                <span>Newest first</span>
              </div>
            </div>
            {list.map((it, i) => (
              <React.Fragment key={it.id}>
                <ArticleRow T={T} D={D} item={it}
                  onPlay={()=>ctx.playItem(it)}
                  onOpen={()=>ctx.openItem(it)}
                  showProgress
                  isPlaying={ctx.nowPlaying?.id===it.id && ctx.playing} />
                {i < list.length-1 && (
                  <div style={{ height:1, background: T.lineSoft, marginLeft:18 }} />
                )}
              </React.Fragment>
            ))}
            <div style={{ height:24 }} />
          </>
        )}
      </ScrollBody>
    </>
  );
}

// ─── Reader / Locus ───────────────────────────────────────────────────────
function ReaderScreen({ T, D, ctx, item }) {
  const isB = T.name.startsWith('B');
  const bodyFamily = isB ? T.serifFamily : T.sansFamily;
  return (
    <>
      <AppBar T={T} title="" onBack={()=>ctx.go('upnext')}
        right={
          <>
            <IconBtn T={T} icon={item.fav ? I.heartFill : I.heart} accent={item.fav} />
            <IconBtn T={T} icon={I.archive} />
            <IconBtn T={T} icon={I.more} />
          </>
        } />
      {/* Bridge banner — green-ish "playing this article" */}
      <div style={{
        padding:'10px 18px', background: T.nowTint,
        borderBottom:`1px solid ${T.nowEdge}33`,
        display:'flex', alignItems:'center', gap:10,
      }}>
        <div style={{ width:6, height:6, borderRadius:3, background: T.accent, boxShadow:`0 0 0 4px ${T.accentDim}` }} />
        <div style={{ flex:1, fontFamily: T.sansFamily, fontSize:11.5, color: T.fg2 }}>
          <span style={{ color: T.accent, fontWeight:600 }}>Now playing</span>
          <span style={{ marginInline:6, color: T.fg4 }}>·</span>
          4:32 / 9:18
        </div>
        <div style={{
          fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.fg2,
          padding:'3px 9px', border:`1px solid ${T.line}`, borderRadius:999,
        }}>1.4×</div>
      </div>
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <div style={{ padding:'24px 22px 16px', maxWidth:640 }}>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.accent,
            letterSpacing:'0.06em', textTransform:'uppercase', marginBottom:10,
          }}>{item.host}</div>
          <h1 style={{
            fontFamily: T.titleFamily, fontWeight: isB ? 600 : 700,
            fontSize: isB ? 28 : 24, lineHeight:1.18, letterSpacing: T.titleTracking,
            color: T.fg, margin:0,
          }}>{item.title}</h1>
          <div style={{
            fontFamily: T.sansFamily, fontSize:12.5, color: T.fg3, marginTop:12,
            display:'flex', alignItems:'center', gap:10,
          }}>
            <span>{item.dur} read</span>
            <span style={{ color: T.fg4 }}>·</span>
            <span>Saved 2 days ago</span>
          </div>
        </div>
        <div style={{ padding:'8px 22px 32px', maxWidth:640 }}>
          {[
            "Weekly insights and analysis on the latest developments in military technology, strategy, and foreign policy.",
            "Northrop Grumman's experimental XRQ-73 Series Hybrid Electric Propulsion AiRcraft Demonstration (SHEPARD) hybrid-electric drone has now taken to the skies. Newly released pictures show that the flying wing-type uncrewed aircraft's design has also evolved since it first broke cover in 2024.",
            "A core goal of SHEPARD is to prove out high-efficiency and very quiet propulsion technology that could pave the way for new operational capabilities.",
            "DARPA announced the XRQ-73 test flight, which was conducted in April from Edwards Air Force Base in California, in a press release today. The Air Force Research Laboratory (AFRL) was also involved in the milestone event.",
            "Scaled Composites, a 'bleeding edge' boutique aircraft design house and wholly-owned subsidiary of Northrop Grumman, built and flew the demonstrator. The team is targeting flight envelope expansion through year-end.",
          ].map((p,i) => (
            <p key={i} style={{
              fontFamily: bodyFamily, fontSize: isB ? 17 : 16, lineHeight: isB ? 1.6 : 1.55,
              color: i===0 ? T.fg2 : T.fg, marginTop: i===0 ? 0 : 18, fontStyle: i===0 ? 'italic' : 'normal',
            }}>{p}</p>
          ))}
          {/* highlight */}
          <p style={{
            fontFamily: bodyFamily, fontSize: isB ? 17 : 16, lineHeight: isB ? 1.6 : 1.55,
            color: T.fg, marginTop: 18,
          }}>
            <span style={{ background: T.accentDim, color: T.fg, padding:'1px 3px', borderRadius:2 }}>
              "The infrastructure already exists; we just need to point it at the right problem,"
            </span> said one program participant.
          </p>
        </div>
      </ScrollBody>
    </>
  );
}

// ─── Up Next ──────────────────────────────────────────────────────────────
function UpNextScreen({ T, D, ctx }) {
  const get = (id) => ITEMS.find(x => x.id === id);
  const now = get(UP_NEXT.nowPlaying);
  return (
    <>
      <AppBar T={T} title="Up Next" onMenu={ctx.openMenu}
        right={
          <>
            <IconBtn T={T} icon={I.refresh} title="Re-seed" />
            <IconBtn T={T} icon={I.add} />
            <IconBtn T={T} icon={I.more} />
          </>
        } />
      <div style={{
        padding:'12px 18px 6px', borderBottom:`1px solid ${T.lineSoft}`,
        display:'flex', alignItems:'center', justifyContent:'space-between',
      }}>
        <div>
          <div style={{
            fontFamily: T.sansFamily, fontSize:12, color: T.fg2,
          }}>
            <span style={{ color: T.accent, fontWeight:600 }}>Session queue</span>
            <span style={{ marginInline:8, color: T.fg4 }}>·</span>
            <span style={{ color: T.fg }}>{UP_NEXT.history.length + UP_NEXT.earlier.length + 1 + UP_NEXT.upcoming.length}</span>
          </div>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, color: T.fg3, marginTop:2,
          }}>Seeded from Smart Queue · 1h ago</div>
        </div>
        <button style={{
          fontFamily: T.sansFamily, fontSize:12, fontWeight:500, color: T.fg2,
          background:'transparent', border:`1px solid ${T.line}`, padding:'6px 12px',
          borderRadius:999, cursor:'pointer',
        }}>Re-seed</button>
      </div>
      <ScrollBody T={T} padBottom={ctx.miniH}>
        {/* History */}
        <SectionHeader T={T} label="History" count={UP_NEXT.history.length} />
        {UP_NEXT.history.map(id => {
          const it = get(id);
          return (
            <div key={id} style={{ opacity:.55 }}>
              <ArticleRow T={T} D={D} item={it} dense
                onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} />
            </div>
          );
        })}
        {/* Earlier in queue */}
        <SectionHeader T={T} label="Earlier in queue" count={UP_NEXT.earlier.length} />
        {UP_NEXT.earlier.map(id => {
          const it = get(id);
          return <ArticleRow key={id} T={T} D={D} item={it}
            onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} dense />;
        })}
        {/* Now Playing — anchored card */}
        <SectionHeader T={T} label="Now playing" />
        <div style={{ padding:'4px 14px 4px' }}>
          <div style={{
            border:`1px solid ${T.nowEdge}`, borderLeftWidth:3,
            background: T.nowTint, borderRadius: T.radiusCard,
            padding:'14px 16px 14px 14px', cursor:'pointer',
          }} onClick={()=>ctx.openItem(now)}>
            <div style={{
              fontFamily: T.sansFamily, fontSize:10.5, fontWeight:600, letterSpacing:'0.08em',
              textTransform:'uppercase', color: T.accent, marginBottom:8,
              display:'flex', alignItems:'center', gap:6,
            }}>
              <div style={{
                width:6, height:6, borderRadius:3, background:T.accent,
                boxShadow:`0 0 0 4px ${T.accentDim}`,
                animation:'mimeoPulse 1.6s ease-in-out infinite',
              }} />
              Now playing · 4:32 / 9:18
            </div>
            <div style={{
              fontFamily: T.titleFamily, fontWeight: T.titleWeight,
              fontSize:17, color: T.fg, lineHeight:1.28, letterSpacing: T.titleTracking,
            }}>{now.title}</div>
            <div style={{
              fontFamily: T.sansFamily, fontSize:12, color: T.fg3, marginTop:6,
            }}>{now.host}</div>
            <div style={{ marginTop:12, height:3, background: T.line, borderRadius:2 }}>
              <div style={{ width:'42%', height:'100%', background: T.accent, borderRadius:2 }} />
            </div>
          </div>
        </div>
        {/* Up Next */}
        <SectionHeader T={T} label="Up Next" count={UP_NEXT.upcoming.length}
          action={{ label:'Clear upcoming', onClick:()=>{} }} />
        {UP_NEXT.upcoming.map((id, i) => {
          const it = get(id);
          return (
            <React.Fragment key={id}>
              <ArticleRow T={T} D={D} item={it} dragHandle
                onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} />
              {i < UP_NEXT.upcoming.length-1 && (
                <div style={{ height:1, background: T.lineSoft, marginLeft:42 }} />
              )}
            </React.Fragment>
          );
        })}
        <div style={{ height:24 }} />
      </ScrollBody>
    </>
  );
}

// ─── Smart Queue ──────────────────────────────────────────────────────────
function SmartQueueScreen({ T, D, ctx }) {
  const [sort, setSort] = React.useState('Newest');
  const sorted = [...ITEMS].slice(0,10);
  return (
    <>
      <AppBar T={T} title="Smart Queue" onMenu={ctx.openMenu}
        right={<><IconBtn T={T} icon={I.search}/><IconBtn T={T} icon={I.more}/></>} />
      <div style={{ padding:'12px 18px 8px', borderBottom:`1px solid ${T.lineSoft}`, background: T.appbarBg }}>
        <div style={{
          display:'flex', alignItems:'center', gap:10,
          padding:'9px 14px', borderRadius:12, background: T.surface, border:`1px solid ${T.line}`,
          color: T.fg3,
        }}>
          {React.cloneElement(I.search, { size:16 })}
          <input placeholder="Search Smart Queue…" style={{
            border:0, background:'transparent', outline:'none', flex:1, color: T.fg,
            fontFamily: T.sansFamily, fontSize:14,
          }} />
        </div>
        <div style={{ display:'flex', gap:6, marginTop:10, overflowX:'auto', scrollbarWidth:'none' }}>
          {['Newest','Oldest','Opened','Progress','All sources'].map(s => (
            <Chip key={s} T={T} active={s===sort} onClick={()=>setSort(s)} sm>{s}</Chip>
          ))}
        </div>
      </div>
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <div style={{
          padding:'14px 18px 8px', display:'flex', alignItems:'center', justifyContent:'space-between',
        }}>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, fontWeight:600, letterSpacing:'0.08em',
            textTransform:'uppercase', color: T.accent,
          }}>Today</div>
          <button style={{
            fontFamily: T.sansFamily, fontSize:12, fontWeight:500, color: T.fg2,
            background:'transparent', border:`1px solid ${T.line}`, padding:'5px 12px 5px 10px',
            borderRadius:999, cursor:'pointer', display:'flex', alignItems:'center', gap:6,
          }}>
            {React.cloneElement(I.play, { size:11 })}
            Play all
          </button>
        </div>
        {sorted.map((it, i) => (
          <React.Fragment key={it.id}>
            <ArticleRow T={T} D={D} item={it}
              onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} />
            {i < sorted.length-1 && (
              <div style={{ height:1, background: T.lineSoft, marginLeft:18 }} />
            )}
          </React.Fragment>
        ))}
        <div style={{ height:24 }} />
      </ScrollBody>
    </>
  );
}

// ─── Manual playlist detail ────────────────────────────────────────────────
function ManualPlaylistScreen({ T, D, ctx, playlist }) {
  const items = ITEMS.slice(0, 7);
  return (
    <>
      <AppBar T={T} title="" onBack={()=>ctx.go('inbox')}
        right={<><IconBtn T={T} icon={I.search}/><IconBtn T={T} icon={I.more}/></>} />
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <div style={{ padding:'8px 18px 16px', borderBottom:`1px solid ${T.lineSoft}` }}>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.fg3,
            letterSpacing:'0.08em', textTransform:'uppercase', marginBottom:8,
            display:'flex', alignItems:'center', gap:6,
          }}>
            {React.cloneElement(I.list, { size:12 })}
            Manual playlist
          </div>
          <div style={{
            fontFamily: T.titleFamily, fontWeight: T.titleWeight,
            fontSize:26, color: T.fg, letterSpacing: T.titleTracking,
          }}>{playlist.name}</div>
          <div style={{
            fontFamily: T.sansFamily, fontSize:12.5, color: T.fg3, marginTop:6,
            display:'flex', alignItems:'center', gap:8,
          }}>
            <span>{playlist.count} items</span>
            <span style={{ color: T.fg4 }}>·</span>
            <span>~ 1h 24m</span>
          </div>
          <div style={{ display:'flex', gap:8, marginTop:14 }}>
            <button style={{
              flex:1, padding:'10px 0', borderRadius:999, border:0,
              background: T.accent, color: T.accentOn, cursor:'pointer',
              fontFamily: T.sansFamily, fontSize:13, fontWeight:600,
              display:'flex', alignItems:'center', justifyContent:'center', gap:6,
            }}>
              {React.cloneElement(I.play, { size:13 })}
              Play all
            </button>
            <button style={{
              padding:'10px 16px', borderRadius:999, border:`1px solid ${T.line}`,
              background:'transparent', color: T.fg, cursor:'pointer',
              fontFamily: T.sansFamily, fontSize:13, fontWeight:500,
            }}>Use as Up Next</button>
          </div>
        </div>
        {items.map((it, i) => (
          <React.Fragment key={it.id}>
            <ArticleRow T={T} D={D} item={it} dragHandle
              onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} />
            {i < items.length-1 && (
              <div style={{ height:1, background: T.lineSoft, marginLeft:42 }} />
            )}
          </React.Fragment>
        ))}
        <div style={{ height:24 }} />
      </ScrollBody>
    </>
  );
}

// ─── Smart playlist detail ─────────────────────────────────────────────────
function SmartPlaylistScreen({ T, D, ctx, playlist }) {
  const items = ITEMS.slice(7);
  return (
    <>
      <AppBar T={T} title="" onBack={()=>ctx.go('inbox')}
        right={<><IconBtn T={T} icon={I.refresh}/><IconBtn T={T} icon={I.more}/></>} />
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <div style={{ padding:'8px 18px 16px', borderBottom:`1px solid ${T.lineSoft}` }}>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.accent,
            letterSpacing:'0.08em', textTransform:'uppercase', marginBottom:8,
            display:'flex', alignItems:'center', gap:6,
          }}>
            {React.cloneElement(I.diamond, { size:12 })}
            Smart playlist · live
          </div>
          <div style={{
            fontFamily: T.titleFamily, fontWeight: T.titleWeight,
            fontSize:26, color: T.fg, letterSpacing: T.titleTracking,
          }}>{playlist.name}</div>
          <div style={{
            fontFamily: T.sansFamily, fontSize:12.5, color: T.fg3, marginTop:6,
            display:'flex', alignItems:'center', gap:8,
          }}>
            <span>{playlist.count} items</span>
            <span style={{ color: T.fg4 }}>·</span>
            <span>updates automatically</span>
          </div>
          {/* Rule chips */}
          <div style={{
            marginTop:14, padding:'10px 12px', background: T.surface,
            border:`1px solid ${T.line}`, borderRadius:T.radiusItem,
          }}>
            <div style={{
              fontFamily: T.sansFamily, fontSize:10.5, fontWeight:600, color: T.fg3,
              letterSpacing:'0.08em', textTransform:'uppercase', marginBottom:6,
            }}>Rule</div>
            <div style={{ display:'flex', gap:6, flexWrap:'wrap' }}>
              <Chip T={T} sm active>Newest first</Chip>
              <Chip T={T} sm>11 domains</Chip>
              <Chip T={T} sm>Last 30 days</Chip>
              <Chip T={T} sm>Unread</Chip>
            </div>
          </div>
          <div style={{ display:'flex', gap:8, marginTop:14 }}>
            <button style={{
              flex:1, padding:'10px 0', borderRadius:999, border:0,
              background: T.accent, color: T.accentOn, cursor:'pointer',
              fontFamily: T.sansFamily, fontSize:13, fontWeight:600,
              display:'flex', alignItems:'center', justifyContent:'center', gap:6,
            }}>
              {React.cloneElement(I.play, { size:13 })}
              Use as Up Next
            </button>
            <button style={{
              padding:'10px 16px', borderRadius:999, border:`1px solid ${T.line}`,
              background:'transparent', color: T.fg, cursor:'pointer',
              fontFamily: T.sansFamily, fontSize:13, fontWeight:500,
            }}>Edit rule</button>
          </div>
        </div>
        <SectionHeader T={T} label="Live results" count={items.length} />
        {items.map((it, i) => (
          <React.Fragment key={it.id}>
            <ArticleRow T={T} D={D} item={it}
              onPlay={()=>ctx.playItem(it)} onOpen={()=>ctx.openItem(it)} />
            {i < items.length-1 && (
              <div style={{ height:1, background: T.lineSoft, marginLeft:18 }} />
            )}
          </React.Fragment>
        ))}
        <div style={{ height:24 }} />
      </ScrollBody>
    </>
  );
}

// ─── Bluesky candidate browser ────────────────────────────────────────────
function BlueskyScreen({ T, D, ctx }) {
  const [savedIds, setSavedIds] = React.useState(new Set(BSKY.filter(b=>b.saved).map(b=>b.id)));
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [list, setList] = React.useState('AI');
  const lists = ['Home Timeline','For You','US Legal','UK Politics','Defense','AI'];
  const visible = BSKY.filter(b => b.list === list);
  const toggle = (id) => {
    const n = new Set(savedIds);
    n.has(id) ? n.delete(id) : n.add(id);
    setSavedIds(n);
  };
  return (
    <>
      <AppBar T={T} title="Bluesky" onMenu={ctx.openMenu}
        right={<><IconBtn T={T} icon={I.refresh}/><IconBtn T={T} icon={I.more}/></>} />
      <ScrollBody T={T} padBottom={ctx.miniH}>
        {/* Hero context band */}
        <div style={{
          padding:'14px 18px 12px', background: T.surface,
          borderBottom:`1px solid ${T.lineSoft}`,
        }}>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11.5, color: T.fg3, lineHeight:1.5,
          }}>
            <span style={{ color: T.fg2, fontWeight:600 }}>Live candidate links.</span>
            {' '}Saving creates normal Mimeo items — nothing is harvested without you.
          </div>
        </div>
        {/* Source selector */}
        <div style={{ padding:'14px 14px 0' }}>
          <div onClick={()=>setPickerOpen(!pickerOpen)} style={{
            border:`1px solid ${T.line}`, borderRadius: T.radiusCard,
            padding:'12px 14px', display:'flex', alignItems:'center', gap:10,
            cursor:'pointer', background: T.surface,
          }}>
            <div style={{
              width:30, height:30, borderRadius:8,
              background: T.accentDim, color: T.accent,
              display:'flex', alignItems:'center', justifyContent:'center',
            }}>{React.cloneElement(I.bsky, { size:14 })}</div>
            <div style={{ flex:1 }}>
              <div style={{
                fontFamily: T.sansFamily, fontSize:11, color: T.fg3, fontWeight:500,
              }}>Source</div>
              <div style={{
                fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:14, color: T.fg,
              }}>Bluesky List · {list}</div>
            </div>
            <div style={{
              transform: pickerOpen ? 'rotate(180deg)' : 'none', transition:'transform 200ms',
              color: T.fg3,
            }}>{React.cloneElement(I.next, { size:14 })}</div>
          </div>
          {pickerOpen && (
            <div style={{
              marginTop:8, padding:12, border:`1px solid ${T.line}`,
              borderRadius: T.radiusCard, background: T.surface,
            }}>
              <div style={{ display:'flex', flexWrap:'wrap', gap:6, marginBottom:10 }}>
                {lists.map(l => (
                  <Chip key={l} T={T} sm active={l===list} onClick={()=>{ setList(l); setPickerOpen(false); }}>{l}</Chip>
                ))}
              </div>
              <div style={{
                padding:'9px 12px', borderRadius:T.radiusItem,
                background: T.bg, border:`1px solid ${T.line}`, color: T.fg3,
                fontFamily: T.sansFamily, fontSize:12.5, marginBottom:6,
              }}>+ Account by handle</div>
              <div style={{
                padding:'9px 12px', borderRadius:T.radiusItem,
                background: T.bg, border:`1px solid ${T.line}`, color: T.fg3,
                fontFamily: T.sansFamily, fontSize:12.5,
              }}>+ Paste list URL</div>
            </div>
          )}
        </div>
        {/* Status pill */}
        <div style={{
          margin:'14px 18px 4px', padding:'10px 14px',
          background: T.accentDim, borderRadius: T.radiusItem,
          fontFamily: T.sansFamily, fontSize:12, color: T.accent,
          display:'flex', alignItems:'center', gap:10,
        }}>
          <div style={{ width:6, height:6, borderRadius:3, background: T.accent }} />
          Scanned 9 posts, {visible.length} links · stop: max_age
          <div style={{ flex:1 }} />
          <span style={{ color: T.fg2, fontWeight:500 }}>Pin source</span>
        </div>
        {/* Candidate cards */}
        <div style={{ padding:'8px 14px 24px' }}>
          {visible.map(b => {
            const isSaved = savedIds.has(b.id);
            return (
              <div key={b.id} style={{
                border:`1px solid ${T.line}`, borderRadius: T.radiusCard,
                padding:'14px 16px', marginBottom:10, background: T.surface,
              }}>
                <div style={{ display:'flex', alignItems:'flex-start', gap:8 }}>
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{
                      fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:15,
                      color: T.fg, lineHeight:1.32, letterSpacing: T.titleTracking,
                    }}>{b.title}</div>
                    <div style={{
                      fontFamily: T.sansFamily, fontSize:12, color: T.fg3, marginTop:4,
                      whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis',
                    }}>{b.url}</div>
                  </div>
                  <div style={{
                    fontFamily: T.sansFamily, fontSize:10.5, fontWeight:600,
                    padding:'3px 8px', borderRadius:999,
                    background: isSaved ? T.accentDim : T.lineSoft,
                    color: isSaved ? T.accent : T.fg3,
                    flexShrink:0,
                  }}>{isSaved ? 'Saved' : 'Unsaved'}</div>
                </div>
                <div style={{
                  fontFamily: T.sansFamily, fontSize:11.5, color: T.fg3, marginTop:8,
                }}>{b.author} · {b.handle} · {b.age}</div>
                <div style={{ marginTop:12, display:'flex', alignItems:'center', gap:6 }}>
                  <button style={{
                    fontFamily: T.sansFamily, fontSize:12, color: T.fg2, fontWeight:500,
                    background:'transparent', border:0, cursor:'pointer', padding:'6px 4px',
                  }}>Open link</button>
                  <span style={{ color: T.fg4 }}>·</span>
                  <button style={{
                    fontFamily: T.sansFamily, fontSize:12, color: T.fg2, fontWeight:500,
                    background:'transparent', border:0, cursor:'pointer', padding:'6px 4px',
                  }}>Open post</button>
                  <div style={{ flex:1 }} />
                  <button onClick={()=>toggle(b.id)} style={{
                    padding:'7px 16px', borderRadius:999, border:0,
                    background: isSaved ? T.lineSoft : T.accent,
                    color: isSaved ? T.fg2 : T.accentOn,
                    fontFamily: T.sansFamily, fontSize:12.5, fontWeight:600, cursor:'pointer',
                    display:'flex', alignItems:'center', gap:5,
                  }}>
                    {isSaved && React.cloneElement(I.check, { size:13 })}
                    {isSaved ? 'Saved' : 'Save'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </ScrollBody>
    </>
  );
}

// ─── Settings overview ────────────────────────────────────────────────────
function SettingsScreen({ T, D, ctx }) {
  const Group = ({ label, children }) => (
    <>
      <SectionHeader T={T} label={label} />
      <div style={{ padding:'0 14px 6px' }}>
        <div style={{
          background: T.surface, border:`1px solid ${T.line}`,
          borderRadius: T.radiusCard, overflow:'hidden',
        }}>{children}</div>
      </div>
    </>
  );
  const Row = ({ icon, label, value, danger, onClick }) => (
    <div onClick={onClick} style={{
      padding:'14px 16px', display:'flex', alignItems:'center', gap:12,
      borderTop:`1px solid ${T.lineSoft}`, cursor:'pointer',
    }}>
      <div style={{
        width:32, height:32, borderRadius:8,
        background: danger ? T.danger+'22' : T.accentDim,
        color: danger ? T.danger : T.accent,
        display:'flex', alignItems:'center', justifyContent:'center',
      }}>{React.cloneElement(icon, { size:16 })}</div>
      <div style={{ flex:1 }}>
        <div style={{ fontFamily: T.sansFamily, fontSize:14.5, color: T.fg, fontWeight:500 }}>{label}</div>
        {value && <div style={{ fontFamily: T.sansFamily, fontSize:12, color: T.fg3, marginTop:2 }}>{value}</div>}
      </div>
      <div style={{ color: T.fg3 }}>{React.cloneElement(I.next, { size:14 })}</div>
    </div>
  );
  return (
    <>
      <AppBar T={T} title="Settings" onMenu={ctx.openMenu} />
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <Group label="Connection">
          <div style={{ padding:'14px 16px' }}>
            <div style={{
              fontFamily: T.sansFamily, fontSize:11, color: T.fg3, fontWeight:500,
              letterSpacing:'0.06em', textTransform:'uppercase', marginBottom:6,
            }}>Mode</div>
            <div style={{ display:'flex', gap:6 }}>
              {['Local','LAN','Remote'].map((m,i) => (
                <Chip key={m} T={T} sm active={i===2}>{m}</Chip>
              ))}
            </div>
            <div style={{
              marginTop:14, padding:'10px 12px', borderRadius: T.radiusItem,
              background: T.bg, border:`1px solid ${T.line}`,
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
              fontSize:12, color: T.fg2,
              whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis',
            }}>https://beh-august2015.taildacac5.ts.net</div>
            <div style={{ display:'flex', gap:8, marginTop:12 }}>
              <button style={{
                flex:1, padding:'9px 0', borderRadius:999, border:0,
                background: T.accent, color: T.accentOn, fontWeight:600,
                fontFamily: T.sansFamily, fontSize:12.5, cursor:'pointer',
              }}>Test connection</button>
              <button style={{
                padding:'9px 14px', borderRadius:999, border:`1px solid ${T.line}`,
                background:'transparent', color: T.fg, fontWeight:500,
                fontFamily: T.sansFamily, fontSize:12.5, cursor:'pointer',
              }}>Diagnostics</button>
            </div>
            <div style={{
              marginTop:10, fontFamily: T.sansFamily, fontSize:11.5, color: T.success,
              display:'flex', alignItems:'center', gap:6,
            }}>
              <div style={{ width:6, height:6, borderRadius:3, background:T.success }}/>
              Signed in · device session active
            </div>
          </div>
        </Group>
        <Group label="Sources">
          <Row icon={I.bsky} label="Bluesky" value="@apstrusor.bsky.social · 6 lists pinned" />
          <Row icon={I.add} label="Add another source" value="Mastodon, RSS — coming later" />
        </Group>
        <Group label="Reading & playback">
          <Row icon={I.book}  label="Reader appearance" value="Direction B · serif body" />
          <Row icon={I.speed} label="Default speed"      value="1.4×" />
          <Row icon={I.queue} label="Up Next behavior"   value="Ask before clearing all" />
        </Group>
        <Group label="Privacy">
          <Row icon={I.shield} label="Privacy & diagnostics"
            value="No titles, URLs or domains in telemetry"
            onClick={()=>ctx.go('privacy')} />
          <Row icon={I.trash} label="Clear local cache" value="42 MB" danger />
        </Group>
        <div style={{
          textAlign:'center', padding:'20px 0 28px',
          fontFamily: T.sansFamily, fontSize:11, color: T.fg3,
        }}>Mimeo · v0.34.2</div>
      </ScrollBody>
    </>
  );
}

// ─── Privacy & diagnostics ────────────────────────────────────────────────
function PrivacyScreen({ T, D, ctx }) {
  return (
    <>
      <AppBar T={T} title="Privacy & diagnostics" onBack={()=>ctx.go('settings')} />
      <ScrollBody T={T} padBottom={ctx.miniH}>
        <div style={{ padding:'18px 18px 8px' }}>
          <div style={{
            background: T.surface, border:`1px solid ${T.line}`,
            borderRadius: T.radiusCard, padding:'18px 18px 16px',
          }}>
            <div style={{
              fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.success,
              letterSpacing:'0.08em', textTransform:'uppercase', marginBottom:10,
              display:'flex', alignItems:'center', gap:6,
            }}>
              <div style={{ width:6, height:6, borderRadius:3, background:T.success }}/>
              Default privacy
            </div>
            <div style={{
              fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:18,
              color: T.fg, letterSpacing: T.titleTracking, lineHeight:1.3,
            }}>What never leaves the device</div>
            <ul style={{ listStyle:'none', padding:0, margin:'12px 0 0' }}>
              {[
                'Article titles',
                'URLs and domains you save',
                'Source identities (Bluesky lists, accounts, feeds)',
                'Which items you opened or finished',
              ].map((t,i)=>(
                <li key={i} style={{
                  display:'flex', alignItems:'flex-start', gap:10, padding:'7px 0',
                  fontFamily: T.sansFamily, fontSize:13, color: T.fg2, lineHeight:1.5,
                }}>
                  <div style={{ color: T.success, marginTop:2 }}>{React.cloneElement(I.check, { size:14 })}</div>
                  {t}
                </li>
              ))}
            </ul>
          </div>
        </div>
        <SectionHeader T={T} label="Optional diagnostics" />
        <div style={{ padding:'0 14px' }}>
          {[
            { l:'Crash reports', s:'Stack traces only. No screen contents.', on:true },
            { l:'Connectivity events', s:'Mode switches and timeouts. No URLs.', on:true },
            { l:'Playback session length', s:'Anonymous totals only.', on:false },
          ].map((opt,i) => (
            <div key={i} style={{
              padding:'14px 16px', background: T.surface, border:`1px solid ${T.line}`,
              borderRadius: T.radiusCard, marginBottom:8,
              display:'flex', alignItems:'center', gap:12,
            }}>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily: T.sansFamily, fontSize:14, color: T.fg, fontWeight:500 }}>{opt.l}</div>
                <div style={{ fontFamily: T.sansFamily, fontSize:12, color: T.fg3, marginTop:3, lineHeight:1.5 }}>{opt.s}</div>
              </div>
              <div style={{
                width:36, height:20, borderRadius:10,
                background: opt.on ? T.accent : T.line, position:'relative', cursor:'pointer',
              }}>
                <div style={{
                  position:'absolute', top:2, left: opt.on ? 18 : 2,
                  width:16, height:16, borderRadius:8, background:'#fff',
                  transition:'left 160ms',
                }} />
              </div>
            </div>
          ))}
        </div>
        <SectionHeader T={T} label="Connectivity" />
        <div style={{ padding:'0 14px 24px' }}>
          <div style={{
            background: T.surface, border:`1px solid ${T.line}`,
            borderRadius: T.radiusCard, padding:'14px 16px',
          }}>
            <div style={{
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize:12, color: T.fg2, lineHeight:1.7,
            }}>
              <div><span style={{ color: T.success }}>OK</span> · TLS handshake 0.18s</div>
              <div><span style={{ color: T.success }}>OK</span> · /healthz 0.21s</div>
              <div><span style={{ color: T.success }}>OK</span> · Token verified · read-write</div>
              <div style={{ color: T.fg3 }}>Last test · 2 min ago</div>
            </div>
            <div style={{ marginTop:12, display:'flex', gap:8 }}>
              <button style={{
                padding:'8px 14px', borderRadius:999, border:`1px solid ${T.line}`,
                background:'transparent', color: T.fg, fontWeight:500,
                fontFamily: T.sansFamily, fontSize:12, cursor:'pointer',
              }}>Run test</button>
              <button style={{
                padding:'8px 14px', borderRadius:999, border:`1px solid ${T.line}`,
                background:'transparent', color: T.fg2, fontWeight:500,
                fontFamily: T.sansFamily, fontSize:12, cursor:'pointer',
              }}>Copy diagnostics</button>
            </div>
          </div>
        </div>
      </ScrollBody>
    </>
  );
}

Object.assign(window, {
  Drawer, InboxScreen, ReaderScreen, UpNextScreen, SmartQueueScreen,
  ManualPlaylistScreen, SmartPlaylistScreen, BlueskyScreen,
  SettingsScreen, PrivacyScreen,
});
