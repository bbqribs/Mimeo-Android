// tokens.jsx — design tokens for both visual directions
// Direction A (Calm Lilac): refinement of current dark+lilac.
// Direction B (Paper & Ember): distinctive editorial cream + ember.

const TOKENS = {
  A: {
    name: 'A · Calm Lilac',
    tag: 'Refinement of current. Calmer dark surface, restrained lilac.',
    // surfaces
    bg:        '#0B0B0E',
    surface:   '#121217',
    surfaceHi: '#181822',
    line:      '#1F1F2A',
    lineSoft:  '#15151D',
    // text
    fg:        '#ECECF1',
    fg2:       '#9A99A6',
    fg3:       '#5E5D6B',
    fg4:       '#3A3947',
    // accent + states
    accent:    '#B6A1FF',          // lilac, slightly desaturated
    accentDim: 'rgba(182,161,255,.14)',
    accentOn:  '#0B0B0E',
    danger:    '#F26E6E',
    success:   '#7FD1A8',
    warn:      '#E8C26A',
    // playback / queue states
    nowTint:   'rgba(182,161,255,0.06)',
    nowEdge:   '#B6A1FF',
    // type
    sansFamily:   '"Inter", "InterVariable", system-ui, -apple-system, sans-serif',
    serifFamily:  '"Source Serif 4", "Source Serif Pro", Georgia, serif',
    titleFamily:  '"Inter", "InterVariable", system-ui, sans-serif',
    titleWeight:  600,
    titleTracking:'-0.011em',
    bodyTracking: '0',
    // shape
    radiusCard: 14,
    radiusItem: 10,
    radiusPill: 999,
    // chrome
    drawerBg:     '#0E0E14',
    drawerSel:    'rgba(182,161,255,0.10)',
    appbarBg:     '#0B0B0E',
    miniPlayerBg: '#15151E',
    miniPlayerLine:'#1F1F2A',
  },
  B: {
    name: 'B · Paper & Ember',
    tag: 'Editorial light reading surface, warm ember accent, serif titles.',
    // surfaces
    bg:        '#F4EFE7',           // warm off-white paper
    surface:   '#FAF7F1',
    surfaceHi: '#FFFCF6',
    line:      '#E2DACB',
    lineSoft:  '#ECE5D6',
    // text
    fg:        '#1B1A17',
    fg2:       '#67625A',
    fg3:       '#9A9388',
    fg4:       '#C7BEAE',
    // accent + states
    accent:    '#C25B2E',           // ember
    accentDim: 'rgba(194,91,46,.10)',
    accentOn:  '#FFFCF6',
    danger:    '#B84A3F',
    success:   '#3F7A52',
    warn:      '#B7892A',
    // playback / queue states
    nowTint:   'rgba(194,91,46,0.07)',
    nowEdge:   '#C25B2E',
    // type
    sansFamily:   '"Inter", "InterVariable", system-ui, -apple-system, sans-serif',
    serifFamily:  '"Source Serif 4", "Source Serif Pro", Georgia, serif',
    titleFamily:  '"Source Serif 4", "Source Serif Pro", Georgia, serif',
    titleWeight:  500,
    titleTracking:'-0.005em',
    bodyTracking: '0',
    // shape
    radiusCard: 12,
    radiusItem: 8,
    radiusPill: 999,
    // chrome
    drawerBg:     '#EFE8DA',
    drawerSel:    'rgba(194,91,46,0.10)',
    appbarBg:     '#F4EFE7',
    miniPlayerBg: '#EFE8DA',
    miniPlayerLine:'#E2DACB',
  },
};

const DENSITY = {
  compact: { rowPadV: 10, rowGap: 2, rowFs: 14, supFs: 11.5, sectGap: 14 },
  regular: { rowPadV: 14, rowGap: 4, rowFs: 15, supFs: 12,   sectGap: 18 },
  comfy:   { rowPadV: 18, rowGap: 6, rowFs: 16, supFs: 13,   sectGap: 22 },
};

// Mock corpus — generic, no real personal data
const ITEMS = [
  { id:'a1', title:"Ancient law requires a bale of straw to hang from Blackfriars Bridge", host:'ianvisits.co.uk',     dur:'7 min',  read:0,    fav:false },
  { id:'a2', title:"How Ukraine Turned Its Defense Into A System Of Battlefield Control", host:'forbes.com',          dur:'14 min', read:0,    fav:true  },
  { id:'a3', title:"DARPA's XRQ-73 Hybrid-Electric Flying Wing Drone Has Flown",          host:'twz.com',             dur:'9 min',  read:0.42, fav:false },
  { id:'a4', title:"USAF Is Going To Explore What Will Finally Replace The F-22",        host:'twz.com',             dur:'12 min', read:0,    fav:false },
  { id:'a5', title:"Using AI To Find Hidden Geothermal Reservoirs",                       host:'forbes.com',          dur:'6 min',  read:0,    fav:false },
  { id:'a6', title:"Google Is Bringing Fitbit App To Pixel Watch Buyers",                 host:'wired.com',           dur:'4 min',  read:0,    fav:false },
  { id:'a7', title:"Researchers Found an Innovative Way to Cut Data Center Energy Use",  host:'gizmodo.com',         dur:'8 min',  read:0,    fav:false },
  { id:'a8', title:"Samuel Alito Had No Answers For Ketanji Brown Jackson's Latest Dissent", host:'ballsandstrikes.org',dur:'11 min',read:1, fav:false },
  { id:'a9', title:"Excerpt — Labour MPs call on Keir Starmer to quit after heavy election defeat", host:'ft.com',  dur:'13 min', read:0,    fav:false },
  { id:'a10',title:"Virginia Supreme Court Overturns Election Because Redistricting Isn't Settled", host:'abovethelaw.com', dur:'10 min', read:0, fav:false },
  { id:'a11',title:"Graduating Law School Amid The Roberts Court's Wreckage",             host:'buttondown.com',      dur:'15 min', read:0,    fav:false },
  { id:'a12',title:"There's an Obvious Reason Why The Republican Justices Sound Skeptical", host:'ballsandstrikes.org', dur:'6 min', read:0, fav:false },
  { id:'a13',title:"Somalis Fled Civil War and Built a Community. Now They Are a Target", host:'nytimes.com',         dur:'18 min', read:0,    fav:true  },
  { id:'a14',title:"Gochujang — origins, culture, and the spread of Korean fermentation", host:'en.wikipedia.org',    dur:'9 min',  read:0,    fav:false },
];

// Up Next session structure
const UP_NEXT = {
  history:   ['a1'],
  earlier:   ['a2'],
  nowPlaying:'a3',
  upcoming:  ['a4','a5','a6','a7'],
};

// Bluesky candidates
const BSKY = [
  { id:'b1', title:"AI Worker Power is Near Its Peak. They're Finally Starting To Use It.",
    url:'api.omarshehata.me/substack-pr…', author:'Garrison Lovely', handle:'@garrisonlovely.bsky.social', age:'2h', saved:false, list:'AI' },
  { id:'b2', title:"Prophecy by Carissa Véliz · 9780385550970",
    url:'penguinrandomhouse.com/books/759692…', author:'Carissa Véliz', handle:'@carissaveliz.bsky.social', age:'7h', saved:false, list:'AI' },
  { id:'b3', title:"The Cop on the Corner Is Our First Line of Defense — Local Police and the Grid",
    url:'warontherocks.com', author:'War on the Rocks', handle:'@warontherocks.bsky.social', age:'just now', saved:true, list:'Defense' },
  { id:'b4', title:"Cogs of War — defense technology for industrial base watchers",
    url:'warontherocks.com', author:'War on the Rocks', handle:'@warontherocks.bsky.social', age:'just now', saved:false, list:'Defense' },
];

const PLAYLISTS = [
  { id:'p1', name:'AI',           kind:'manual', count:6 },
  { id:'p2', name:'UK politics',  kind:'manual', count:113 },
  { id:'p3', name:'SmokeTest',    kind:'manual', count:8 },
  { id:'p4', name:'US Legal',     kind:'smart',  count:19, rule:'newest first · 11 domains' },
  { id:'p5', name:'Defense',      kind:'smart',  count:42, rule:'newest first · 5 domains · 30d' },
];

Object.assign(window, { TOKENS, DENSITY, ITEMS, UP_NEXT, BSKY, PLAYLISTS });
