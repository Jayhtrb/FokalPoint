"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowRight, CalendarDays, Check, ChevronDown, Heart, MapPin, Search, SlidersHorizontal, Star, Video, Camera, Brush, MonitorPlay, Mic2, Grid2X2 } from "lucide-react";

const creators = [
  { slug:"arjun-menon", name:"Arjun Menon", role:"Photographer · Videographer", city:"Hyderabad", rating:"4.9", reviews:128, price:"₹15,000", verified:true, image:"https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=900&q=85", work:"Wedding · Editorial" },
  { slug:"wedding-lens", name:"The Wedding Lens", role:"Wedding Photography", city:"Hyderabad", rating:"4.8", reviews:216, price:"₹35,000", verified:true, image:"https://images.unsplash.com/photo-1537633552985-df8429e8048b?auto=format&fit=crop&w=900&q=85", work:"Wedding · Pre-wedding" },
  { slug:"rhea-captures", name:"Rhea Captures", role:"Portrait Photographer", city:"Bengaluru", rating:"4.9", reviews:94, price:"₹12,000", verified:true, image:"https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85", work:"Portrait · Fashion" },
  { slug:"frame-motion", name:"Frame & Motion", role:"Videographer · Editor", city:"Mumbai", rating:"4.8", reviews:167, price:"₹25,000", verified:true, image:"https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?auto=format&fit=crop&w=900&q=85", work:"Film · Commercial" },
  { slug:"kabir-studios", name:"Kabir Studios", role:"Photo · Film · Reels", city:"Pune", rating:"5.0", reviews:211, price:"₹22,000", verified:true, image:"https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=900&q=85", work:"Brand · Events" },
  { slug:"maya-stories", name:"Maya Stories", role:"Content Creator", city:"Hyderabad", rating:"4.9", reviews:86, price:"₹10,000", verified:false, image:"https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85", work:"Lifestyle · Social" },
];
const categories = [
  {label:"Photography",icon:Camera},{label:"Videography",icon:Video},{label:"Makeup & Styling",icon:Brush},{label:"Editing & Post",icon:MonitorPlay},{label:"Events & Production",icon:Mic2},{label:"All services",icon:Grid2X2}
];
const occasions = [
  {name:"Weddings",image:"https://images.unsplash.com/photo-1519741497674-611481863552?auto=format&fit=crop&w=900&q=85"},
  {name:"Pre-weddings",image:"https://images.unsplash.com/photo-1532712938310-34cb3982ef74?auto=format&fit=crop&w=900&q=85"},
  {name:"Events",image:"https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=900&q=85"},
  {name:"Portraits",image:"https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85"},
  {name:"Commercial",image:"https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=900&q=85"},
];

export default function Home() {
  const [query,setQuery]=useState("");
  const [city,setCity]=useState("Anywhere");
  const [category,setCategory]=useState("All");
  const [saved,setSaved]=useState<string[]>([]);
  const filtered=useMemo(()=>creators.filter(c=>{
    const q=query.trim().toLowerCase();
    return (!q || (c.name+" "+c.role+" "+c.city+" "+c.work).toLowerCase().includes(q)) &&
      (city==="Anywhere" || c.city===city) &&
      (category==="All" || c.role.toLowerCase().includes(category.toLowerCase().replace("photography","photographer").replace("videography","video")));
  }),[query,city,category]);
  const toggle=(slug:string)=>setSaved(v=>v.includes(slug)?v.filter(x=>x!==slug):[...v,slug]);
  return <main className="fp-shell">
    <header className="fp-nav fp-wrap">
      <Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link>
      <nav><a className="active" href="#discover">Discover</a><a href="#creators">Creators</a><a href="#services">Services⌄</a><a href="#for-creators">For Creators</a></nav>
      <div className="fp-nav-actions"><button className="icon-btn" aria-label="Search"><Search size={18}/></button><Link className="fp-btn fp-btn-outline" href="/discover">Explore</Link><Link className="fp-btn fp-btn-gold" href="#for-creators">Join as a Creator</Link></div>
    </header>

    <section className="fp-hero fp-wrap">
      <div className="fp-hero-copy">
        <span className="eyebrow">THE CREATIVE PROFESSIONALS MARKETPLACE</span>
        <h1>Every Moment<br/>in <em>Focus.</em></h1>
        <p>Discover and book photographers, videographers and creative professionals who bring your ideas to life.</p>
        <div className="fp-hero-actions"><a className="fp-btn fp-btn-gold" href="#creators">Explore creators <ArrowRight size={16}/></a><a className="fp-btn fp-btn-outline" href="#how">How it works</a></div>
      </div>
      <div className="fp-hero-image"><img src="https://images.unsplash.com/photo-1537633552985-df8429e8048b?auto=format&fit=crop&w=1400&q=90" alt="A couple sharing a quiet moment on their wedding day"/><div className="image-caption"><span>REAL STORIES. BEAUTIFULLY TOLD.</span><strong>Made for the moments that matter.</strong></div></div>
      <div className="fp-searchbar">
        <div className="fp-search-input"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search creators, styles or services…"/></div>
        <label className="fp-location"><MapPin size={17}/><select value={city} onChange={e=>setCity(e.target.value)}><option>Hyderabad</option><option>Bengaluru</option><option>Mumbai</option><option>Pune</option><option>Anywhere</option></select><ChevronDown size={14}/></label>
        <a className="fp-search-submit" href="#creators">Find a creator <ArrowRight size={16}/></a>
      </div>
    </section>

    <section className="fp-wrap fp-categories" id="services">
      {categories.map(({label,icon:Icon})=><button key={label} className={category===label?"selected":""} onClick={()=>setCategory(label==="All services"?"All":label)}><span><Icon size={20}/></span>{label}</button>)}
    </section>

    <section className="fp-wrap fp-section" id="creators">
      <div className="fp-section-head"><div><span className="eyebrow">A GOOD PLACE TO START</span><h2>Meet your next creative partner.</h2><p>Distinctive talent, thoughtful craft, work worth remembering.</p></div><Link href="/discover" className="fp-text-link">View all creators <ArrowRight size={16}/></Link></div>
      <div className="fp-creator-grid">{filtered.slice(0,4).map(c=><article className="fp-creator-card" key={c.slug}><Link href={"/creators/"+c.slug} className="fp-card-image"><img src={c.image} alt={c.name}/><span className="fp-card-type">{c.work}</span></Link><button className={"fp-save "+(saved.includes(c.slug)?"saved":"")} onClick={()=>toggle(c.slug)} aria-label={"Save "+c.name}><Heart size={17} fill={saved.includes(c.slug)?"currentColor":"none"}/></button><div className="fp-card-body"><div className="fp-card-name"><Link href={"/creators/"+c.slug}>{c.name}</Link>{c.verified&&<span className="fp-verified"><Check size={11}/></span>}</div><p>{c.role}</p><div className="fp-card-meta"><span><Star size={13} fill="currentColor"/> {c.rating} <small>({c.reviews})</small></span><span>From <strong>{c.price}</strong></span></div><div className="fp-card-city"><MapPin size={12}/>{c.city}</div></div></article>)}</div>
      {filtered.length===0&&<div className="fp-empty">No creators match those filters yet. Try another location or search term.</div>}
    </section>

    <section className="fp-wrap fp-section fp-occasion-section">
      <div className="fp-section-head"><div><span className="eyebrow">FIND THE RIGHT FIT</span><h2>What are you creating?</h2><p>Start with the occasion. We’ll help you find the people.</p></div><a className="fp-text-link" href="#creators">Explore all <ArrowRight size={16}/></a></div>
      <div className="fp-occasion-grid">{occasions.map(o=><a href="#creators" className="fp-occasion" key={o.name}><img src={o.image} alt=""/><span>{o.name}<ArrowRight size={15}/></span></a>)}</div>
    </section>

    <section className="fp-wrap fp-creator-banner" id="for-creators"><div><span className="eyebrow">FOR THE PEOPLE BEHIND THE LENS</span><h2>Your work deserves<br/><em>to be discovered.</em></h2><p>Build your profile, showcase your portfolio and grow your creative business with FokalPoint.</p><a className="fp-btn fp-btn-gold" href="#top">Create your creator profile <ArrowRight size={16}/></a></div><img src="https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=1000&q=85" alt="Professional camera on a creative set"/></section>
    <footer className="fp-footer fp-wrap"><Link href="/" className="fp-brand"><span className="fp-mark">●</span>FokalPoint</Link><span>Every Moment in Focus.</span><small>© 2026 FokalPoint. Made for creative people.</small></footer>
  </main>;
}
