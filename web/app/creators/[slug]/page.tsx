import Link from "next/link";
import { ArrowLeft, CalendarDays, CheckCircle2, Heart, MessageCircle, MapPin, Star } from "lucide-react";

const profiles: Record<string, any> = {
  "amit-sharma": { name:"Amit Sharma", type:"Photographer", city:"Mumbai, India", rating:4.9, reviews:120, projects:250, years:8, price:8000, bio:"Cinematic portraits, weddings and brand stories with a documentary eye. Amit works best with people who care about honest moments and polished delivery.", avatar:"https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=700&q=85", work:["https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=900&q=85","https://images.unsplash.com/photo-1583939003579-730e3918a45a?auto=format&fit=crop&w=900&q=85","https://images.unsplash.com/photo-1606216794074-735e91aa2c92?auto=format&fit=crop&w=900&q=85"] },
  "riya-sen": { name:"Riya Sen", type:"Photographer · Reels", city:"Delhi, India", rating:4.8, reviews:98, projects:182, years:6, price:12000, bio:"Editorial portraits and social-first storytelling with a sharp eye for styling, light and movement.", avatar:"https://images.unsplash.com/photo-1544005313-94ddf0288fdf?auto=format&fit=crop&w=700&q=85", work:["https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=900&q=85","https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=85","https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=900&q=85"] },
};

export default async function CreatorProfile({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const profile = profiles[slug] ?? profiles["amit-sharma"];
  return <main className="shell">
    <header className="container nav"><Link className="logo" href="/"><span className="logoMark"/>FokalPoint</Link><Link href="/discover" className="btn btnGhost"><ArrowLeft size={15}/> Discover</Link></header>
    <section className="container section">
      <div style={{display:"grid",gridTemplateColumns:"330px 1fr",gap:40,alignItems:"start"}}>
        <div><img src={profile.avatar} alt={profile.name} style={{width:"100%",height:430,objectFit:"cover",borderRadius:24}}/><div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:9,marginTop:10}}><button className="btn"><Heart size={15}/> Save</button><button className="btn"><MessageCircle size={15}/> Message</button></div></div>
        <div><span className="badge"><CheckCircle2 size={11}/> Verified creator</span><h1 style={{fontFamily:"Manrope",fontSize:"clamp(42px,6vw,68px)",letterSpacing:"-3px",lineHeight:1,margin:"8px 0 10px"}}>{profile.name}</h1><div style={{color:"var(--muted)",display:"flex",gap:16,alignItems:"center",fontSize:14}}><span>{profile.type}</span><span><MapPin size={13} style={{verticalAlign:"-2px"}}/> {profile.city}</span><span><Star size={12} fill="#ffb43d" color="#ffb43d"/> {profile.rating} ({profile.reviews})</span></div>
          <p style={{fontSize:18,lineHeight:1.7,color:"#c0c1ca",maxWidth:680,margin:"28px 0"}}>{profile.bio}</p>
          <div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",borderTop:"1px solid var(--line)",borderBottom:"1px solid var(--line)",padding:"20px 0",gap:20}}><div><strong style={{fontSize:25}}>{profile.projects}</strong><div className="meta">Projects</div></div><div><strong style={{fontSize:25}}>{profile.years}</strong><div className="meta">Years experience</div></div><div><strong style={{fontSize:25}}>{profile.rating}</strong><div className="meta">Average rating</div></div></div>
          <div style={{display:"flex",gap:10,marginTop:24}}><button className="btn btnPrimary"><CalendarDays size={16}/> Check availability</button><button className="btn">Starting from ₹{profile.price.toLocaleString("en-IN")}</button></div>
        </div>
      </div>
    </section>
    <section className="container section" style={{paddingTop:10}}><div className="sectionTitle"><h2>Selected work</h2><p>A small window into the creator's visual language.</p></div><div style={{display:"grid",gridTemplateColumns:"repeat(3,1fr)",gap:14}}>{profile.work.map((src:string,i:number)=><img key={src} src={src} alt={`Selected work ${i+1}`} style={{width:"100%",height:330,objectFit:"cover",borderRadius:18}}/>)}</div></section>
  </main>;
}
