// atoms.jsx — small UI primitives used across screens.
// Receives tokens (T) and density (D) as props for direction-agnostic rendering.

// ─── Icons (stroke-based, lightweight) ──────────────────────────────────────
const Icon = ({ d, size=20, stroke='currentColor', fill='none', sw=1.6, style }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={stroke}
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={style}>
    {d}
  </svg>
);
const I = {
  menu:  <Icon d={<><path d="M4 7h16"/><path d="M4 12h16"/><path d="M4 17h16"/></>} />,
  back:  <Icon d={<path d="M15 6l-6 6 6 6"/>} />,
  search:<Icon d={<><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></>} />,
  more:  <Icon d={<><circle cx="12" cy="5" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="12" cy="19" r="1.3"/></>} fill="currentColor" sw={0}/>,
  add:   <Icon d={<><path d="M12 5v14"/><path d="M5 12h14"/></>} />,
  refresh:<Icon d={<><path d="M3 12a9 9 0 0 1 15.5-6.3L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15.5 6.3L3 16"/><path d="M3 21v-5h5"/></>} />,
  play:  <Icon d={<path d="M8 5l12 7-12 7z"/>} fill="currentColor" sw={0}/>,
  pause: <Icon d={<><rect x="7" y="5" width="3.5" height="14" rx="1"/><rect x="13.5" y="5" width="3.5" height="14" rx="1"/></>} fill="currentColor" sw={0}/>,
  prev:  <Icon d={<><path d="M6 5v14"/><path d="M20 5l-12 7 12 7z"/></>} fill="currentColor" sw={0}/>,
  next:  <Icon d={<><path d="M18 5v14"/><path d="M4 5l12 7-12 7z"/></>} fill="currentColor" sw={0}/>,
  ff:    <Icon d={<><path d="M3 5l8 7-8 7z"/><path d="M13 5l8 7-8 7z"/></>} fill="currentColor" sw={0}/>,
  rw:    <Icon d={<><path d="M21 5l-8 7 8 7z"/><path d="M11 5l-8 7 8 7z"/></>} fill="currentColor" sw={0}/>,
  heart: <Icon d={<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>} />,
  heartFill: <Icon d={<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>} fill="currentColor" sw={0}/>,
  archive:<Icon d={<><rect x="3" y="4" width="18" height="4" rx="1"/><path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8"/><path d="M10 13h4"/></>} />,
  trash: <Icon d={<><path d="M4 7h16"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13"/><path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></>} />,
  drag:  <Icon d={<><path d="M8 7h8"/><path d="M8 12h8"/><path d="M8 17h8"/></>} />,
  close: <Icon d={<><path d="M6 6l12 12"/><path d="M6 18L18 6"/></>} />,
  check: <Icon d={<path d="M5 12l4 4 10-10"/>} />,
  speed: <Icon d={<><path d="M12 4a8 8 0 1 0 8 8"/><path d="M12 12l5-5"/></>} />,
  book:  <Icon d={<><path d="M4 5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2V5z"/><path d="M6 17h12"/></>} />,
  inbox: <Icon d={<><path d="M3 13l3-7h12l3 7"/><path d="M3 13v6h18v-6"/><path d="M8 13h2l1 2h2l1-2h2"/></>} />,
  star:  <Icon d={<path d="M12 4l2.5 5.5 6 .6-4.5 4 1.4 6-5.4-3.2L6.6 20l1.4-6L3.5 10l6-.6z"/>} />,
  starF: <Icon d={<path d="M12 4l2.5 5.5 6 .6-4.5 4 1.4 6-5.4-3.2L6.6 20l1.4-6L3.5 10l6-.6z"/>} fill="currentColor" sw={0}/>,
  queue: <Icon d={<><path d="M4 6h13"/><path d="M4 12h13"/><path d="M4 18h9"/><path d="M17 16v6l5-3z"/></>} />,
  smart: <Icon d={<><path d="M12 3l1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7z"/><path d="M19 15l.8 2 2 .8-2 .8L19 21l-.8-2-2-.8 2-.8z"/></>} />,
  list:  <Icon d={<><circle cx="5" cy="6" r="1.2" fill="currentColor"/><circle cx="5" cy="12" r="1.2" fill="currentColor"/><circle cx="5" cy="18" r="1.2" fill="currentColor"/><path d="M9 6h12"/><path d="M9 12h12"/><path d="M9 18h12"/></>} />,
  bsky:  <Icon d={<path d="M6 4c2.5 1 4.5 3 6 6 1.5-3 3.5-5 6-6 2 0 3 1.5 3 3.5 0 1.6-1 4-3 6-1.6 1.6-3.7 3-6 4.5-2.3-1.5-4.4-2.9-6-4.5-2-2-3-4.4-3-6C3 5.5 4 4 6 4z"/>} fill="currentColor" sw={0}/>,
  cog:   <Icon d={<><circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1.4l2.1-1.6-2-3.4-2.4 1a7 7 0 0 0-2.4-1.4L13.7 3h-3.4l-.5 2.2a7 7 0 0 0-2.4 1.4l-2.4-1-2 3.4L5.1 10.6A7 7 0 0 0 5 12c0 .5 0 1 .1 1.4l-2.1 1.6 2 3.4 2.4-1a7 7 0 0 0 2.4 1.4l.5 2.2h3.4l.5-2.2a7 7 0 0 0 2.4-1.4l2.4 1 2-3.4-2.1-1.6c.1-.4.1-.9.1-1.4z"/></>} />,
  shield:<Icon d={<><path d="M12 3l8 3v6c0 5-3.5 8.5-8 9-4.5-.5-8-4-8-9V6z"/></>} />,
  jump:  <Icon d={<><path d="M5 12h14"/><path d="M14 7l5 5-5 5"/></>} />,
  diamond:<Icon d={<path d="M6 4h12l4 6-10 11L2 10z"/>} />,
  link:  <Icon d={<><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/></>} />,
};

// ─── Surface / Section helpers ──────────────────────────────────────────────
const SectionHeader = ({ T, label, count, action }) => (
  <div style={{
    display:'flex', alignItems:'baseline', justifyContent:'space-between',
    padding:'14px 18px 6px',
  }}>
    <div style={{
      fontFamily: T.sansFamily, fontSize:11, fontWeight:600,
      letterSpacing:'0.08em', textTransform:'uppercase', color: T.fg2,
    }}>
      {label}{count != null && <span style={{ color: T.fg3, marginLeft:8, fontWeight:500 }}>{count}</span>}
    </div>
    {action && (
      <button onClick={action.onClick} style={{
        background:'transparent', border:0, color: T.fg2, cursor:'pointer',
        fontFamily: T.sansFamily, fontSize:12, fontWeight:500, padding:'4px 6px',
        borderRadius:6,
      }}>{action.label}</button>
    )}
  </div>
);

// ─── Article row ───────────────────────────────────────────────────────────
function ArticleRow({ T, D, item, onPlay, onOpen, onMore, dragHandle, showProgress, dense, isPlaying }) {
  const padV = dense ? D.rowPadV - 2 : D.rowPadV;
  return (
    <div style={{
      display:'flex', alignItems:'flex-start', gap:10,
      padding:`${padV}px 18px`, position:'relative', cursor:'pointer',
    }} onClick={onOpen}>
      {dragHandle && (
        <div style={{
          color:T.fg4, paddingTop:2, marginLeft:-4, flexShrink:0,
          display:'flex', alignItems:'center', height:22,
        }}>{I.drag}</div>
      )}
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{
          fontFamily: T.titleFamily, fontWeight: T.titleWeight,
          fontSize: D.rowFs, color: T.fg, lineHeight: 1.32,
          letterSpacing: T.titleTracking,
          display:'-webkit-box', WebkitLineClamp:2, WebkitBoxOrient:'vertical',
          overflow:'hidden',
        }}>{item.title}</div>
        <div style={{
          fontFamily: T.sansFamily, fontSize: D.supFs, color: T.fg3,
          marginTop: D.rowGap + 2, display:'flex', alignItems:'center', gap:8,
        }}>
          <span>{item.host}</span>
          <span style={{ color: T.fg4 }}>·</span>
          <span>{item.dur}</span>
          {item.fav && (
            <>
              <span style={{ color: T.fg4 }}>·</span>
              <span style={{ color: T.accent, display:'inline-flex' }}>{React.cloneElement(I.starF, { size:12 })}</span>
            </>
          )}
        </div>
        {showProgress && item.read > 0 && (
          <div style={{ marginTop: 8, height: 2, background: T.lineSoft, borderRadius:1, overflow:'hidden', maxWidth:240 }}>
            <div style={{ height:'100%', width:`${item.read*100}%`, background: T.accent, opacity: 0.7 }} />
          </div>
        )}
      </div>
      <div style={{ display:'flex', alignItems:'center', gap:2, flexShrink:0, marginTop:-2 }}>
        <IconBtn T={T} icon={isPlaying ? I.pause : I.play} onClick={(e)=>{ e.stopPropagation(); onPlay && onPlay(); }} accent />
        <IconBtn T={T} icon={I.more} onClick={(e)=>{ e.stopPropagation(); onMore && onMore(); }} />
      </div>
    </div>
  );
}

// ─── IconBtn ────────────────────────────────────────────────────────────────
function IconBtn({ T, icon, onClick, accent, size=36, iconSize=18, style, title }) {
  return (
    <button onClick={onClick} title={title} style={{
      width:size, height:size, borderRadius: size/2,
      background:'transparent', border:0, cursor:'pointer',
      display:'flex', alignItems:'center', justifyContent:'center',
      color: accent ? T.accent : T.fg2,
      transition:'background 120ms',
      ...style,
    }}
    onMouseEnter={e=>{ e.currentTarget.style.background = T.surfaceHi; }}
    onMouseLeave={e=>{ e.currentTarget.style.background = 'transparent'; }}
    >
      {React.cloneElement(icon, { size: iconSize })}
    </button>
  );
}

// ─── Pill / chip ───────────────────────────────────────────────────────────
function Chip({ T, children, active, onClick, sm }) {
  return (
    <button onClick={onClick} style={{
      padding: sm ? '4px 10px' : '6px 14px',
      borderRadius: 999,
      fontFamily: T.sansFamily, fontSize: sm ? 11 : 12.5, fontWeight:500,
      border:`1px solid ${active ? T.accent : T.line}`,
      background: active ? T.accentDim : 'transparent',
      color: active ? T.accent : T.fg2,
      cursor:'pointer', whiteSpace:'nowrap',
    }}>{children}</button>
  );
}

// ─── Mini-player (bottom) ──────────────────────────────────────────────────
function MiniPlayer({ T, item, playing, setPlaying, onOpen, onJump, showJump, glow, speed=1.4 }) {
  if (!item) return null;
  return (
    <div style={{
      borderTop:`1px solid ${T.miniPlayerLine}`,
      background: glow ? `linear-gradient(180deg, ${T.nowTint}, ${T.miniPlayerBg})` : T.miniPlayerBg,
      padding:'10px 14px 12px',
      position:'relative',
    }}>
      {showJump && (
        <div style={{
          position:'absolute', top:-18, left:'50%', transform:'translateX(-50%)',
          background: T.accent, color: T.accentOn,
          padding:'5px 12px', borderRadius:999,
          fontFamily: T.sansFamily, fontSize: 11.5, fontWeight:600,
          boxShadow:'0 4px 14px rgba(0,0,0,.25)', cursor:'pointer',
          display:'flex', alignItems:'center', gap:6,
        }} onClick={onJump}>
          {React.cloneElement(I.jump, { size:13 })}
          Jump to Now Playing
        </div>
      )}
      {/* progress bar */}
      <div style={{ height:2, background: T.line, borderRadius:1, position:'relative', marginBottom:9 }}>
        <div style={{ height:'100%', width:'42%', background: T.accent, borderRadius:1 }} />
      </div>
      {/* title row */}
      <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:8, cursor:'pointer' }} onClick={onOpen}>
        <div style={{ flex:1, minWidth:0 }}>
          <div style={{
            fontFamily: T.titleFamily, fontWeight: T.titleWeight,
            fontSize:13.5, color: T.fg,
            whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis',
            letterSpacing: T.titleTracking,
          }}>{item.title}</div>
          <div style={{
            fontFamily: T.sansFamily, fontSize:11, color: T.fg3, marginTop:2,
          }}>{item.host} · 4:32 / 9:18</div>
        </div>
      </div>
      {/* controls */}
      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div style={{
          fontFamily: T.sansFamily, fontSize:11, fontWeight:600, color: T.fg2,
          padding:'3px 9px', border:`1px solid ${T.line}`, borderRadius:999,
        }}>{speed.toFixed(1)}×</div>
        <div style={{ display:'flex', alignItems:'center', gap:0 }}>
          <IconBtn T={T} icon={I.prev} size={32} iconSize={16} />
          <IconBtn T={T} icon={I.rw} size={36} iconSize={18} />
          <button onClick={()=>setPlaying(!playing)} style={{
            width:42, height:42, borderRadius:21, marginInline:4,
            background: T.accent, color: T.accentOn, border:0, cursor:'pointer',
            display:'flex', alignItems:'center', justifyContent:'center',
          }}>{React.cloneElement(playing ? I.pause : I.play, { size:18 })}</button>
          <IconBtn T={T} icon={I.ff} size={36} iconSize={18} />
          <IconBtn T={T} icon={I.next} size={32} iconSize={16} />
        </div>
        <button style={{
          width:32, height:32, borderRadius:16, border:`1px solid ${T.line}`, background:'transparent',
          color: T.fg2, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center',
        }}>{React.cloneElement(I.queue, { size:14 })}</button>
      </div>
    </div>
  );
}

// ─── Top app bar (custom — not Material) ───────────────────────────────────
function AppBar({ T, title, onMenu, onBack, right }) {
  return (
    <div style={{
      height:54, padding:'0 6px', display:'flex', alignItems:'center', gap:4,
      background: T.appbarBg, borderBottom:`1px solid ${T.lineSoft}`,
      position:'sticky', top:0, zIndex:5,
    }}>
      <IconBtn T={T} icon={onBack ? I.back : I.menu} onClick={onBack || onMenu} size={44} iconSize={22} />
      <div style={{
        flex:1, fontFamily: T.titleFamily, fontWeight: T.titleWeight,
        fontSize:18, color: T.fg, letterSpacing: T.titleTracking,
      }}>{title}</div>
      <div style={{ display:'flex', alignItems:'center', gap:0, paddingRight:4 }}>{right}</div>
    </div>
  );
}

// ─── Status bar (matches our token bg) ─────────────────────────────────────
function StatusBar({ T }) {
  const dark = T.bg.charAt(1) < '8';
  const c = dark ? T.fg : T.fg;
  return (
    <div style={{
      height:32, padding:'0 18px', display:'flex', alignItems:'center', justifyContent:'space-between',
      fontFamily: T.sansFamily, fontSize:12, fontWeight:600, color: c,
      background: T.bg,
    }}>
      <span>9:30</span>
      <div style={{ display:'flex', alignItems:'center', gap:5, opacity:.85 }}>
        <svg width="14" height="11" viewBox="0 0 14 11"><path d="M0 8l3-3 4 4 7-7v8a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2z" fill={c}/></svg>
        <svg width="15" height="11" viewBox="0 0 15 11"><path d="M7.5 1a10 10 0 0 1 7 2.8L13 5.3a8 8 0 0 0-11 0L.5 3.8A10 10 0 0 1 7.5 1zm0 3a7 7 0 0 1 4.9 2L11 7.5a5 5 0 0 0-7 0L2.6 6A7 7 0 0 1 7.5 4zm0 3a4 4 0 0 1 2.8 1.2L7.5 11l-2.8-2.8A4 4 0 0 1 7.5 7z" fill={c}/></svg>
        <svg width="22" height="11" viewBox="0 0 22 11"><rect x="0.5" y="0.5" width="19" height="10" rx="2.5" stroke={c} fill="none"/><rect x="2" y="2" width="13" height="7" rx="1" fill={c}/><rect x="20" y="3.5" width="1.5" height="4" rx="0.5" fill={c}/></svg>
      </div>
    </div>
  );
}

// ─── Phone shell ───────────────────────────────────────────────────────────
function Phone({ T, children, width=380, height=820 }) {
  const dark = parseInt(T.bg.slice(1,3),16) < 0x80;
  return (
    <div style={{
      width, height, borderRadius:36, overflow:'hidden',
      background: T.bg, color: T.fg,
      border:`8px solid ${dark ? '#222228' : '#D6CFBE'}`,
      boxShadow:'0 30px 80px rgba(0,0,0,0.35), 0 6px 18px rgba(0,0,0,0.18)',
      display:'flex', flexDirection:'column', position:'relative',
      fontFamily: T.sansFamily,
    }}>
      <StatusBar T={T} />
      <div style={{ flex:1, display:'flex', flexDirection:'column', overflow:'hidden', position:'relative' }}>
        {children}
      </div>
      <div style={{ height:24, display:'flex', alignItems:'center', justifyContent:'center', background:T.bg }}>
        <div style={{ width:108, height:4, borderRadius:2, background: T.fg, opacity:.35 }} />
      </div>
    </div>
  );
}

// ─── Scrollable body inside phone ──────────────────────────────────────────
function ScrollBody({ T, children, padBottom=0 }) {
  return (
    <div style={{
      flex:1, overflowY:'auto', overflowX:'hidden',
      paddingBottom: padBottom,
      scrollbarWidth:'none',
    }}
    className="mimeo-scroll"
    >{children}</div>
  );
}

// ─── Empty state ───────────────────────────────────────────────────────────
function EmptyState({ T, icon, title, body }) {
  return (
    <div style={{
      padding:'60px 32px', textAlign:'center', color: T.fg3,
    }}>
      <div style={{
        width:48, height:48, borderRadius:24, background: T.surface,
        display:'inline-flex', alignItems:'center', justifyContent:'center',
        color: T.fg3, marginBottom:16,
      }}>{icon}</div>
      <div style={{
        fontFamily: T.titleFamily, fontWeight: T.titleWeight, fontSize:17,
        color: T.fg, marginBottom:6, letterSpacing: T.titleTracking,
      }}>{title}</div>
      <div style={{ fontFamily: T.sansFamily, fontSize:13, color: T.fg3, lineHeight:1.5, maxWidth:260, margin:'0 auto' }}>
        {body}
      </div>
    </div>
  );
}

Object.assign(window, {
  I, Icon, SectionHeader, ArticleRow, IconBtn, Chip, MiniPlayer, AppBar, StatusBar, Phone, ScrollBody, EmptyState,
});
