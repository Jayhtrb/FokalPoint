"use client";

import { useMemo, useState } from "react";
import { ArrowRight, CalendarDays, CheckCircle2, Heart, MessageCircle, Search, Sparkles, Star, Users, WalletCards } from "lucide-react";

const creators = [
  { name: "Rhea Kapoor", role: "Photographer · Videographer", city: "Bengaluru", rating: 5.0, reviews: 324, price: "₹18,000", verified: true, category: "Photo", image: "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=85" },
  { name: "Amit Sharma", role: "Photographer", city: "Mumbai", rating: 4.9, reviews: 120, price: "₹8,000", verified: true, category: "Photo", image: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=900&q=85" },
  { name: "Priya Patel", role: "Makeup Artist", city: "Hyderabad", rating: 4.8, reviews: 98, price: "₹5,000", verified: true, category: "Events", image: "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=900&q=85" },
  { name: "Rahul Verma", role: "Videographer", city: "Delhi", rating: 4.9, reviews: 156, price: "₹12,000", verified: true, category: "Video", image: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=900&q=85" },
  { name: "Sneha Kapoor", role: "Content Creator", city: "Pune", rating: 4.7, reviews: 86, price: "₹10,000", verified: false, category: "More", image: "https://images.unsplash.com/photo-1531123897727-8f129e1688ce?auto=format&fit=crop&w=900&q=85" },
  { name: "Kabir Studios", role: "Photo · Film · Reels", city: "Bengaluru", rating: 5.0, reviews: 211, price: "₹25,000", verified: true, category: "Video", image: "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=900&q=85" },
];

const categories = ["All", "Photo", "Video", "Events", "More"];

export default function Home() {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("All");
  const [city, setCity] = useState("All cities");
  const [saved, setSaved] = useState<string[]>([]);

  const filtered = useMemo(() => creators.filter((creator) => {
    const q = query.trim().toLowerCase();
    const matchesQuery = !q || `${creator.name} ${creator.role} ${creator.city}`.toLowerCase().includes(q);
    const matchesCategory = category === "All" || creator.category === category;
    const matchesCity = city === "All cities" || creator.city === city;
    return matchesQuery && matchesCategory && matchesCity;
  }), [query, category, city]);

  const toggleSaved = (name: string) => setSaved((current) => current.includes(name) ? current.filter((item) => item !== name) : [...current, name]);

  return (
    <main className="shell">
      <header className="container nav">
        <a className="logo" href="#top"><span className="logoMark" />FokalPoint</a>
        <nav className="navLinks" aria-label="Main navigation">
          <a href="#discover">Discover</a><a href="#how">How it works</a><a href="#creators">For creators</a><a href="#about">About</a>
        </nav>
        <div className="navActions"><button className="btn btnGhost">Sign in</button><button className="btn btnPrimary">Join FokalPoint</button></div>
      </header>

      <section id="top" className="container hero">
        <div>
          <h1>The right creator.<br />The right project.<br /><span className="gradient">All in focus.</span></h1>
          <p>FokalPoint brings photographers, videographers and creative professionals together with the people who need exceptional work done.</p>
          <div className="heroActions"><a className="btn btnPrimary" href="#discover">Explore creators <ArrowRight size={16} /></a><a className="btn" href="#how">See how it works</a></div>
          <div className="stats">
            <div className="stat"><strong>1,000+</strong><span>Verified creators</span></div><div className="stat"><strong>5,000+</strong><span>Projects completed</span></div><div className="stat"><strong>98%</strong><span>Client satisfaction</span></div><div className="stat"><strong>50+</strong><span>Cities covered</span></div>
          </div>
        </div>
        <div className="heroVisual" aria-label="FokalPoint product preview">
          <img className="heroPhoto" src="https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?auto=format&fit=crop&w=1100&q=85" alt="Photographer working at sunset" />
          <div className="creatorPanel">
            <div className="panelHeader"><strong>Find the perfect creator</strong><span style={{color:"#777"}}>Client workspace</span></div>
            <div className="searchBox"><Search size={13} /><span>What are you looking for?</span><span>Search</span></div>
            <div className="categoryRow"><div className="category">Photography<br /><small>360+ creators</small></div><div className="category">Videography<br /><small>280+ creators</small></div><div className="category">Events<br /><small>180+ creators</small></div></div>
            <div style={{fontSize:12,fontWeight:600,marginBottom:9}}>Top creators</div>
            <div className="creatorGrid">{creators.slice(0,3).map(c => <div className="creatorCard" key={c.name}><img src={c.image} alt=""/><div><strong>{c.name}</strong><br/><small>{c.role}</small></div></div>)}</div>
          </div>
          <div className="phone"><img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=85" alt="Creator profile preview" /></div>
        </div>
      </section>

      <section id="discover" className="container section">
        <div className="sectionTitle"><div><h2>Discover people who get your vision.</h2></div><p>Search by skill, location or project type. Save your favourites, compare portfolios and start a conversation before you book.</p></div>
        <div className="discovery">
          <aside className="filters">
            <h3>Refine your search</h3>
            <label className="filterLabel" htmlFor="search">Search</label>
            <div className="searchBox"><Search size={14}/><input id="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Name, skill, city..." style={{background:'transparent',border:0,outline:0,color:'#fff',width:'100%'}} /></div>
            <label className="filterLabel" htmlFor="city">Location</label>
            <select className="filterInput" id="city" value={city} onChange={(e) => setCity(e.target.value)}><option>All cities</option><option>Mumbai</option><option>Delhi</option><option>Bengaluru</option><option>Hyderabad</option><option>Pune</option></select>
            <label className="filterLabel">Category</label>
            <div style={{display:'grid',gap:7}}>{categories.map(item => <button key={item} onClick={() => setCategory(item)} className="btn" style={{textAlign:'left',padding:'9px 10px',background:item===category?'rgba(242,38,117,.12)':'transparent',borderColor:item===category?'rgba(242,38,117,.35)':'var(--line)'}}>{item}</button>)}</div>
          </aside>
          <div className="creatorResults" id="creators">
            {filtered.map((creator) => <article className="bigCreator" key={creator.name}>
              <div style={{position:'relative'}}><img src={creator.image} alt={`${creator.name}, ${creator.role}`} /><button aria-label={`Save ${creator.name}`} onClick={() => toggleSaved(creator.name)} style={{position:'absolute',right:10,top:10,width:34,height:34,borderRadius:10,border:'1px solid rgba(255,255,255,.16)',background:'rgba(0,0,0,.45)',color:saved.includes(creator.name)?'#ff4e90':'#fff',display:'grid',placeItems:'center'}}><Heart size={16} fill={saved.includes(creator.name)?'currentColor':'none'}/></button></div>
              <div className="bigCreatorBody">{creator.verified && <span className="badge"><CheckCircle2 size={11} style={{marginRight:4}}/>Verified</span>}<h3>{creator.name}</h3><div className="meta">{creator.role}</div><div className="meta">{creator.city} · <Star size={10} fill="#ffb43d" color="#ffb43d" style={{verticalAlign:'-1px'}}/> {creator.rating} ({creator.reviews})</div><div className="price"><span>Starting from</span><strong>{creator.price}</strong></div></div>
            </article>)}
            {filtered.length === 0 && <div style={{gridColumn:'1/-1',padding:'50px 20px',textAlign:'center',color:'var(--muted)'}}>No creators match those filters. Try a broader search.</div>}
          </div>
        </div>
      </section>

      <section id="how" className="container section">
        <div className="sectionTitle"><h2>From idea to done.</h2><p>FokalPoint turns the messy part of finding creative talent into a clear, trusted workflow.</p></div>
        <div className="workflow">
          {[['01','Discover','Find the right specialist with portfolios, reviews, location and availability in one place.'],['02','Connect','Chat directly, share your brief and align on scope, dates and expectations.'],['03','Book & pay','Confirm the project with transparent pricing and secure payment handling.'],['04','Create','Get the work done, review the outcome and build relationships for what comes next.']].map(([num,title,copy]) => <div className="workflowItem" key={num}><span className="workflowNum">{num}</span><h3>{title}</h3><p>{copy}</p></div>)}
        </div>
      </section>

      <section id="about" className="container section creatorSpotlight">
        <img className="spotlightImage" src="https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=1200&q=85" alt="Creative professional on location" />
        <div className="spotlightCopy"><h2>Built for creators. Designed for momentum.</h2><p>FokalPoint is not just another directory. It is a workspace for the entire creative relationship, from first search to final delivery.</p><ul className="featureList"><li><span className="featureIcon"><Sparkles size={15}/></span>AI-assisted discovery and matching</li><li><span className="featureIcon"><MessageCircle size={15}/></span>Real-time conversations and project briefs</li><li><span className="featureIcon"><CalendarDays size={15}/></span>Availability-aware booking</li><li><span className="featureIcon"><WalletCards size={15}/></span>Secure payments and creator payouts</li><li><span className="featureIcon"><Users size={15}/></span>Profiles that turn work into reputation</li></ul><button className="btn btnPrimary">Create your FokalPoint <ArrowRight size={16}/></button></div>
      </section>

      <footer className="footer"><div className="container footerInner"><span>© 2026 FokalPoint. Create · Connect · Grow.</span><span>Built for the people who bring ideas to life.</span></div></footer>
    </main>
  );
}
